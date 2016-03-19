package org.apache.nutch.parse.lq.mapper;

import org.apache.nutch.parse.lq.po.CrawlData;

import java.util.List;
/**
 *  昶乐
 */
public interface CrawlDataMapper {
    int insertBatch(List<CrawlData> list);
    List<CrawlData> queryByFetchTime(String fetchTime,int limit);
}
