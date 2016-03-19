package org.apache.nutch.parse.lq.po;

import org.apache.solr.client.solrj.beans.Field;
/**
 *  昶乐
 */
public class CrawlData {
    @Field("id")
    private String url;
    @Field
    private String content;
    @Field
    private String category;
    @Field("fetch_time")
    private String fetchTime;

    public CrawlData() {

    }

    public CrawlData(String url, String content, String category, String fetchTime) {
        this.url = url;
        this.content = content;
        this.category = category;
        this.fetchTime = fetchTime;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url == null ? null : url.trim();
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content == null ? null : content.trim();
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category == null ? null : category.trim();
    }

    public String getFetchTime() {
        return fetchTime;
    }

    public void setFetchTime(String fetchTime) {
        this.fetchTime = fetchTime == null ? null : fetchTime.trim();
    }
}
