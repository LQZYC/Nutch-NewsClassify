package org.apache.nutch.parse.lq.newsclassify;

import com.coremedia.iso.Hex;
import org.apache.avro.util.Utf8;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.apache.nutch.parse.HTMLMetaTags;
import org.apache.nutch.parse.Outlink;
import org.apache.nutch.parse.Parse;
import org.apache.nutch.parse.lq.po.CrawlData;
import org.apache.nutch.parse.lq.statistics.FocuseLinkCount;
import org.apache.nutch.parse.lq.svm.SvmResult;
import org.apache.nutch.storage.WebPage;
import org.apache.nutch.util.StringUtil;
import org.apache.solr.client.solrj.impl.BinaryRequestWriter;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DocumentFragment;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *  昶乐
 */
public class NewsClassifyHtmlParseFilter extends org.apache.nutch.parse.lq.AbstractHtmlParseFilter {

    public static final  Logger                                    LOG           = LoggerFactory.getLogger(NewsClassifyHtmlParseFilter.class);
    private static final org.apache.nutch.parse.lq.svm.SvmCheck    svmCheck      = new org.apache.nutch.parse.lq.svm.SvmCheck();
    private static final ConcurrentHashMap<String,FocuseLinkCount> MAP           = new ConcurrentHashMap<>();
    @Override protected String getUrlFilterRegex() {
        return null;
    }

    @Override protected boolean isParseDataFetchLoadedInternal(String url, String html) {
        return true;
    }

    @Override protected boolean isContentMatchedForParse(String url, String html) {
        return true;
    }

    @Override public Parse filterInternal(String url, WebPage page, Parse parse, HTMLMetaTags metaTags, DocumentFragment doc) throws Exception {
        getClassifyDate(parse,new Date(),url,page);
        return parse;
    }

    /**
     *
     * 如果用xpath来提取网页中的元素可以参考以下代码 该代码以新浪网页作为示例
     * @throws Exception
     */
       private void getDataForSina(DocumentFragment doc,WebPage page,Date date) throws Exception {
           List<String> urlArrs = new ArrayList<>();
           List<String> names = new ArrayList<>();

           String urlArr1  = getXPathValueByToken(doc, "//DIV[@id='syncad_1']/H1/A/@href");
           String name1  = getXPathValueByToken(doc, "//DIV[@id='syncad_1']/H1/A/text()");
           if (!(StringUtil.isEmpty(urlArr1)||StringUtil.isEmpty(name1))){
               Collections.addAll(urlArrs, tokenizeToStringArray(urlArr1, ";", true, true));
               Collections.addAll(names,tokenizeToStringArray(name1,";",true,true));
           }

           String urlArr2  = getXPathValueByToken(doc, "//UL[@class='list_14']/LI/A/@href");
           String name2  = getXPathValueByToken(doc, "//UL[@class='list_14']/LI/A/text()");
           if (!(StringUtil.isEmpty(urlArr2)||StringUtil.isEmpty(name2))) {
               Collections.addAll(urlArrs, tokenizeToStringArray(urlArr2, ";", true, true));
               Collections.addAll(names, tokenizeToStringArray(name2, ";", true, true));
           }

            if (urlArrs.isEmpty()||names.isEmpty()){
            }
        }


       private void  getClassifyDate(Parse parse,Date date,String url,WebPage page) throws Exception {
           Outlink[] outLinks = parse.getOutlinks();
           if (outLinks.length == 0){
               return;
           }
           List<CrawlData> crawlDatas = new ArrayList<>();
           for (Outlink o : outLinks) {
               if (StringUtil.isEmpty(o.getAnchor())) {
                   continue;
               }
              SvmResult svmResult = svmCheck.runLoadModelAndUse(getConf(), o.getAnchor());

              addScoreToPageMark(o,svmResult,page);

              addLink(svmResult,new URL(url).getHost());
               if (!(svmResult.isExceedThreshold())){
                   continue;
               }
               //有的链接过长,是的varchar(255)不够存,所以将url进行MD5加密后作为主键id,然后定义url为text来存储链接
               crawlDatas.add(new CrawlData(Hex.encodeHex(DigestUtils.md5(o.getToUrl())), o.getToUrl(), o.getAnchor(), svmResult.getCategory(), getNowTime(date), svmResult.getScore()));
           }
           if (crawlDatas.isEmpty()){
               return;
           }
           int update_num = saveDataToMysql(crawlDatas);
           //如果更新成功就建立索引
           if (update_num != 0){
               addIndexToSolr(crawlDatas);
           }
           for (Map.Entry entry : MAP.entrySet()){
               //System.out.println(entry.getValue());
               LOG.info(entry.getValue().toString());
           }
       }

    private void addScoreToPageMark(Outlink o,SvmResult svmResult,WebPage page) {
        if (svmResult.getCategory().equals("体育")){
            page.getMarkers().put(new Utf8(o.getToUrl()),new Utf8(String.valueOf(svmResult.getScore())));
        }
    }

    private void addIndexToSolr(List<CrawlData> crawlDatas) {
        try {
            String solrUrl = getConf().get("solr.server.url");
            if (StringUtil.isEmpty(solrUrl)){
                LOG.info("没有配置\"solr.server.url\"属性,忽略索引");
                return;
            }
            HttpSolrServer server = new HttpSolrServer(solrUrl);
            server.setRequestWriter(new BinaryRequestWriter());
            server.addBeans(crawlDatas.iterator());
            server.optimize();
            LOG.info("Adding index {} data to NutchDocument", crawlDatas.size());
        } catch (Exception e) {
            LOG.error("批量更新索引失败",e);
        }
    }
    private void addLink(SvmResult svmResult,String urlMark){
        FocuseLinkCount focuseLinkCount = MAP.get(urlMark);
        if (focuseLinkCount == null){
             focuseLinkCount = new FocuseLinkCount(urlMark);
            MAP.put(urlMark,focuseLinkCount);
        }
        focuseLinkCount.addLinkCount(svmResult);
    }

    @Override protected void statFocusePageNumber(String url, AtomicInteger focusedPages,Parse parse) {
        try {
            SvmResult svmResult = svmCheck.runLoadModelAndUse(getConf(), parse.getTitle());
            if (svmResult.getCategory().equals("体育")){
                focusedPages.incrementAndGet();
            }
            LOG.info("爬取总网页数为:"+getPages().get()+"  爬取的体育主题网页数为:"+focusedPages.get());
        } catch (Exception e) {
            LOG.error("THUCTC系统分类异常",e);
        }
    }

    @Override protected void writeHtmlToTxt(String text, int order) {
        String dst = "hdfs://120.25.162.238:9000/train/"+order+".txt";
        InputStream in;
        try {
            in = org.apache.commons.io.IOUtils.toInputStream(text, "UTF-8");
            Configuration conf = new Configuration();
            FileSystem fs = FileSystem.get(URI.create(dst), conf);
            OutputStream out = fs.create(new Path(dst));
            IOUtils.copyBytes(in, out, 4096, true);
        } catch (IOException e) {
           LOG.error("写网页内容到hadoop环境发生错误",e);
        }
    }
}
