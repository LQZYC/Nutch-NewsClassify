package org.apache.nutch.parse.lq;

import com.sun.org.apache.xpath.internal.XPathAPI;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.nutch.parse.HTMLMetaTags;
import org.apache.nutch.parse.Parse;
import org.apache.nutch.parse.ParseFilter;
import org.apache.nutch.parse.lq.mapper.CrawlDataMapper;
import org.apache.nutch.parse.lq.po.CrawlData;
import org.apache.nutch.parse.lq.util.MyBatisUtil;
import org.apache.nutch.plugin.Extension;
import org.apache.nutch.plugin.ExtensionPoint;
import org.apache.nutch.plugin.PluginRepository;
import org.apache.nutch.plugin.PluginRuntimeException;
import org.apache.nutch.storage.WebPage;
import org.apache.nutch.storage.WebPage.Field;
import org.apache.nutch.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;


public abstract class AbstractHtmlParseFilter implements ParseFilter {
    public static final  Logger LOG                   = LoggerFactory.getLogger(AbstractHtmlParseFilter.class);
    private static final String HTMLPARSEFILTER_ORDER = "htmlparsefilter.order";
    private static final long   start                 = System.currentTimeMillis(); // start time of fetcher run
    // 获取解析过滤器集合，用于过滤链回调判断页面加载完成
    private static AbstractHtmlParseFilter[] parseFilters        ;
    private        Transformer               transformer;
    private AtomicInteger pages        = new AtomicInteger(0); // total pages fetched
    private AtomicInteger focusedPages = new AtomicInteger(0);//focuse pages fetched
    private Pattern filterPattern;
    private Configuration conf;
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    @SuppressWarnings("rawtypes")
    private static ThreadLocal threadLocal = new ThreadLocal() {
        protected synchronized Object initialValue() {
            return new SimpleDateFormat(DATE_FORMAT);
        }
    };

    private static DateFormat getDateFormat() {
        return (DateFormat) threadLocal.get();
    }

    protected static String getNowTime(Date date){
        return getDateFormat().format(date);
    }
    /**
     * 基于xpath获取Node列表
     */
    private NodeList selectNodeList(Node node, String xpath) {
        try {
            return XPathAPI.selectNodeList(node, xpath);
        } catch (TransformerException e) {
            LOG.warn("Bad 'xpath' expression [{}]", xpath);
        }
        return null;
    }

    /**
     * 帮助类方法：获取当前所有配置的自定义过滤器集合
     */
    public static AbstractHtmlParseFilter[] getParseFilters(Configuration conf) {
        // Prepare parseFilters
        String order = conf.get(HTMLPARSEFILTER_ORDER);
        if (parseFilters == null) {
            /*
             * If ordered filters are required, prepare array of filters based on property
             */
            String[] orderedFilters = null;
            if (order != null && !order.trim().equals("")) {
                orderedFilters = order.split("\\s+");
            }
            HashMap<String, AbstractHtmlParseFilter> filterMap = new HashMap<>();
            try {
                ExtensionPoint point = PluginRepository.get(conf).getExtensionPoint(ParseFilter.X_POINT_ID);
                if (point == null) throw new RuntimeException(ParseFilter.X_POINT_ID + " not found.");
                Extension[] extensions = point.getExtensions();
                for (Extension extension : extensions) {
                    ParseFilter parseFilter = (ParseFilter) extension.getExtensionInstance();
                    if (parseFilter instanceof AbstractHtmlParseFilter) {
                        if (!filterMap.containsKey(parseFilter.getClass().getName())) {
                            filterMap.put(parseFilter.getClass().getName(), (AbstractHtmlParseFilter) parseFilter);
                        }
                    }
                }
                parseFilters = filterMap.values().toArray(new AbstractHtmlParseFilter[filterMap.size()]);
                if (orderedFilters != null) {
                    ArrayList<ParseFilter> filters = new ArrayList<>();
                    for (String orderedFilter : orderedFilters) {
                        ParseFilter filter = filterMap.get(orderedFilter);
                        if (filter != null) {
                            filters.add(filter);
                        }
                    }
                    parseFilters = filters.toArray(new AbstractHtmlParseFilter[filters.size()]);
                }
            } catch (PluginRuntimeException e) {
                throw new RuntimeException(e);
            }
        }
        return parseFilters;
    }

    protected static String[] tokenizeToStringArray(String str, String delimiters, boolean trimTokens, boolean ignoreEmptyTokens) {

        if (str == null) {
            return null;
        }
        StringTokenizer st = new StringTokenizer(str, delimiters);
        List<String> tokens = new ArrayList<>();
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if (trimTokens) {
                token = token.trim();
            }
            if (!ignoreEmptyTokens || token.length() > 0) {
                tokens.add(token);
            }
        }
        return toStringArray(tokens);
    }

    private static String[] toStringArray(Collection<String> collection) {
        if (collection == null) {
            return null;
        }
        return collection.toArray(new String[collection.size()]);
    }



    public Configuration getConf() {
        return this.conf;
    }

    public void setConf(Configuration conf) {
        this.conf = conf;
        String filterRegex = getUrlFilterRegex();
        if (StringUtils.isNotBlank(filterRegex)) {
            this.filterPattern = Pattern.compile(getUrlFilterRegex());
        }
        try {
            transformer = TransformerFactory.newInstance().newTransformer();
            // transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            // transformer.setOutputProperty(OutputKeys.INDENT, "no");
            // transformer.setOutputProperty(OutputKeys.METHOD, "html");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected String getXPathValueByToken(Node contextNode, String xpath) {
        NodeList nodes = selectNodeList(contextNode, xpath);
        if (nodes == null || nodes.getLength() <= 0) {
            return null;
        }
        String txt = "";
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Text) {
                txt += node.getNodeValue();
            } else {
                txt += node.getTextContent();
            }
            txt += ";";
        }
        return cleanInvisibleChar(txt);

    }


    private String asString(Node node) {
        if (node == null) {
            return "";
        }
        try {
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(node), new StreamResult(writer));
            String xml = writer.toString();
            xml = StringUtils.substringAfter(xml, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            xml = xml.trim();
            return xml;
        } catch (Exception e) {
            throw new IllegalArgumentException("error for parse node to string.", e);
        }
    }
    /**
     * 清除无关的不可见空白字符
     */
    private String cleanInvisibleChar(String str) {
        return cleanInvisibleChar(str, false);
    }

    /**
     * 清除无关的不可见空白字符
     */
    private String cleanInvisibleChar(String str, boolean includingBlank) {
        if (str != null) {
            str = StringUtils.remove(str, (char) 160);
            if (includingBlank) {
                // 普通空格
                str = StringUtils.remove(str, " ");
                // 全角空格
                str = StringUtils.remove(str, (char) 12288);
            }
            str = StringUtils.remove(str, "\r");
            str = StringUtils.remove(str, "\n");
            str = StringUtils.remove(str, "\t");
            str = StringUtils.remove(str, "\\s*");
            str = StringUtils.remove(str, "◆");
            str = StringUtil.cleanField(str);
            str = str.trim();
        }
        return str;
    }

    /**
     * 清除无关的Node节点元素
     */
    private void cleanUnusedNodes(Node doc) {
        cleanUnusedNodes(doc, "//STYLE");
        cleanUnusedNodes(doc, "//MAP");
        cleanUnusedNodes(doc, "//SCRIPT");
        cleanUnusedNodes(doc, "//script");
    }

    /**
     * 清除无关的Node节点元素
     */
    private void cleanUnusedNodes(Node node, String xpath) {
        try {
            NodeList nodes = XPathAPI.selectNodeList(node, xpath);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element element = (Element) nodes.item(i);
                element.getParentNode().removeChild(element);
            }
        } catch (DOMException | TransformerException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Parse filter(String url, WebPage page, Parse parse, HTMLMetaTags metaTags, DocumentFragment doc) {
        LOG.debug("Invoking parse  {} for url: {}", this.getClass().getName(), url);
        try {
            // URL匹配
            if (!isUrlMatchedForParse(url)) {
                LOG.debug("Skipped {} as not match regex [{}]", this.getClass().getName(), getUrlFilterRegex());
                return parse;
            }

            if (page.getContent() == null) {
                LOG.warn("Empty content for url: {}", url);
                return parse;
            }

            // 检测内容是否业务关注页面
            String html = asString(doc);
            if (!isContentMatchedForParse(url, html)) {
                LOG.debug("Skipped as content not match excepted");
                return parse;
            }

            // 清除无关的Node节点元素
            cleanUnusedNodes(doc);
            pages.incrementAndGet();
            //把网页内容写到hadoop环境的txt文件中
            writeHtmlToTxt(parse.getText(),pages.get());

            //统计主题页面采集数量
            statFocusePageNumber(url,focusedPages, parse);
            parse = filterInternal(url, page, parse, metaTags, doc);

            if (LOG.isInfoEnabled()) {
                long elapsed = (System.currentTimeMillis() - start) / 1000;
                float avgPagesSec = (float) pages.get() / elapsed;
                LOG.info(" - Custom prased total " + pages.get() + " pages, " + elapsed + " seconds, avg " + avgPagesSec
                         + " pages/s");
            }
            return parse;
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
        return null;
    }

    protected abstract void statFocusePageNumber(String url, AtomicInteger focusedPages,Parse parse);

    protected abstract void writeHtmlToTxt(String text,int order);

    /**
     * 通过mybatis框架进行持久化处理
     */
    protected int saveDataToMysql(List<CrawlData> crawlDatas) {
        try (SqlSession sqlSession = MyBatisUtil.getSqlSessionFactory(this.conf).openSession()) {
            CrawlDataMapper crawlDataMapper = sqlSession.getMapper(CrawlDataMapper.class);
            int updatenum = crawlDataMapper.insertBatch(crawlDatas);
            sqlSession.commit();
            return updatenum;
        }
    }

    @Override
    public Collection<Field> getFields() {
        return null;
    }

    /**
     * 判断url是否符合自定义解析匹配规则
     */
    private boolean isUrlMatchedForParse(String url) {
        // 没有url控制规则，直接放行
        return filterPattern == null || filterPattern.matcher(url).find();
    }

    /**
     * 检测url获取页面内容是否已加载完毕，主要用于支持一些AJAX页面延迟等待加载 返回false则表示告知Fetcher处理程序继续AJAX执行短暂等待后再回调此方法直到返回true标识内容已加载完毕
     * @return 默认返回true，子类根据需要定制判断逻辑
     */
    public boolean isParseDataFetchLoaded(String url, String html) {
        if (filterPattern == null) {
            // 没有url控制规则，直接放行
            return true;
        }
        // 首先判断url是否匹配当前过滤器，如果是则继续调用内容判断逻辑
        return !filterPattern.matcher(url).find() || !StringUtils.isBlank(html) && isParseDataFetchLoadedInternal(url, html);
    }


    /**
     * 设置当前解析过滤器匹配的URL正则表达式 只有匹配的url才调用当前解析处理逻辑
     */
    protected abstract String getUrlFilterRegex();

    /**
     * 检测url获取页面内容是否已加载完毕，主要用于支持一些AJAX页面延迟等待加载 返回false则表示告知Fetcher处理程序继续AJAX执行短暂等待后再回调此方法直到返回true标识内容已加载完毕
     *
     * @param html 页面HTML
     * @return 默认返回true，子类根据需要定制判断逻辑
     */
    protected abstract boolean isParseDataFetchLoadedInternal(String url, String html);

    /**
     * 判断当前页面内容是否业务关注的页面
     */
    protected abstract boolean isContentMatchedForParse(String url, String html);

    /**
     * 子类实现具体的页面数据解析逻辑
     */
    public abstract Parse filterInternal(String url, WebPage page, Parse parse, HTMLMetaTags metaTags,
                                         DocumentFragment doc) throws Exception;

    public AtomicInteger getPages() {
        return pages;
    }
    public AtomicInteger getFocusedPages() {
        return focusedPages;
    }

}
