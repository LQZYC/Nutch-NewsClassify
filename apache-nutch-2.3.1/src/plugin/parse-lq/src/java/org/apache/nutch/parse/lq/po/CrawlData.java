package org.apache.nutch.parse.lq.po;

import org.apache.solr.client.solrj.beans.Field;
/**
 *  昶乐
 */
public class CrawlData {
    @Field("id")
    private String id;
    @Field("url")
    private String url;
    @Field
    private String content;
    @Field
    private String category;
    @Field("fetch_time")
    private String fetchTime;
    @Field("relevance_score")
    private double relevanceScore;
    public CrawlData() {

    }

    public CrawlData(String id, String url, String content, String category, String fetchTime, double relevanceScore) {
        this.id = id;
        this.url = url;
        this.content = content;
        this.category = category;
        this.fetchTime = fetchTime;
        this.relevanceScore = relevanceScore;
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(double relevanceScore) {
        this.relevanceScore = relevanceScore;
    }
}
