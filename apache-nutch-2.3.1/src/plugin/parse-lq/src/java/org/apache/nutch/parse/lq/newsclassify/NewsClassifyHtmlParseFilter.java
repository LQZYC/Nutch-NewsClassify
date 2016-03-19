package org.apache.nutch.parse.lq.newsclassify;

import org.apache.nutch.parse.HTMLMetaTags;
import org.apache.nutch.parse.Outlink;
import org.apache.nutch.parse.Parse;
import org.apache.nutch.parse.lq.po.CrawlData;
import org.apache.nutch.parse.lq.svm.SvmCheck;
import org.apache.nutch.storage.WebPage;
import org.apache.nutch.util.StringUtil;
import org.apache.solr.client.solrj.impl.BinaryRequestWriter;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DocumentFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
/**
 *  昶乐
 */
public class NewsClassifyHtmlParseFilter extends org.apache.nutch.parse.lq.AbstractHtmlParseFilter {

    public static final  Logger                                 LOG      = LoggerFactory.getLogger(NewsClassifyHtmlParseFilter.class);
    private static final org.apache.nutch.parse.lq.svm.SvmCheck svmCheck = new org.apache.nutch.parse.lq.svm.SvmCheck();
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
        getClassifyDate(parse,page,new Date());
        return parse;
    }

    /**
     *
     * 如果用xpath来提取网页中的元素可以参考以下代码 该代码以新浪网页作为示例
     * @throws Exception
     */
       private void getDataForSina(DocumentFragment doc,WebPage page,Date date) throws Exception {
           List<CrawlData> crawlDatas = new ArrayList<>();
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
                return;
            }
           for (int i=0;i < names.size();i++){
               crawlDatas.add(new CrawlData(urlArrs.get(i), names.get(i), svmCheck.runLoadModelAndUse(getConf(), names.get(i)), getNowTime(date)));
           }
           injectDataToPageMetadata(page.getBatchId().toString(),crawlDatas.size(),page);
           saveDataToMysql(crawlDatas);
        }


       private void  getClassifyDate(Parse parse,WebPage page,Date date) throws Exception {
           Outlink[] outLinks = parse.getOutlinks();
           List<CrawlData> crawlDatas = new ArrayList<>();
           String category;
           for (Outlink o : outLinks) {
               if (StringUtil.isEmpty(o.getAnchor())) {
                   continue;
               }
               category = svmCheck.runLoadModelAndUse(getConf(), o.getAnchor());
               if (category.equals(SvmCheck.OTHER)){
                   continue;
               }
               crawlDatas.add(new CrawlData(o.getToUrl(), o.getAnchor(), category, getNowTime(date)));
           }
           if (crawlDatas.isEmpty()){
               return;
           }
           injectDataToPageMetadata(getNowTime(date),crawlDatas.size(),page);
           int update_num = saveDataToMysql(crawlDatas);
           //如果更新成功就建立索引
           if (update_num != 0){
               addIndexToSolr(crawlDatas);
           }
       }
    public void addIndexToSolr(List<CrawlData> crawlDatas) {
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
}
