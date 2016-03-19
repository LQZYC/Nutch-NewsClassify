/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.nutch.crawl;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.nutch.fetcher.FetcherJob;
import org.apache.nutch.indexer.IndexingJob;
import org.apache.nutch.indexer.solr.SolrDeleteDuplicates;
import org.apache.nutch.metadata.Nutch;
import org.apache.nutch.parse.ParserJob;
import org.apache.nutch.util.NutchConfiguration;
import org.apache.nutch.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

// Commons Logging imports

public class Crawl extends Configured implements Tool {
  public static final Logger LOG = LoggerFactory.getLogger(Crawl.class);

  /* Perform complete crawling and indexing (to Solr) given a set of root urls and the -solr
     parameter respectively. More information and Usage parameters can be found below. */
  public static void main(String args[]) throws Exception {
    Configuration conf = NutchConfiguration.create();
    String[] parameter = new String[3];
    parameter[0] = "urls";
    parameter[1] = "testcrawlid";
//    parameter[2] = "http://localhost:8080/solr";
//    parameter[3] = "1";
      parameter[2] = "1";
    int res = ToolRunner.run(conf, new Crawl(), parameter);
    System.exit(res);
  }
  
  @Override
  public int run(String[] args) throws Exception {
    if (args.length < 3) {
      System.out.println
      ("Usage: crawl <seedDir> <crawlID> [<solrUrl>] <numberOfRounds>");
      return -1;
    }
    String seedDir = args[0];
    String crawlId = args[1];
    String limit="",solrUrl="";
      if (args.length==3){
        limit = args[2];
    }else if (args.length==4){
          solrUrl = args[2];
          limit = args[3];
      }else {
          System.out.println("参数个数不匹配,检查输入参数");

      }

      if (StringUtil.isEmpty(seedDir)){
          System.out.println("Missing seedDir : crawl <seedDir> <crawlID> [<solrURL>] <numberOfRounds>");
      }

      if (StringUtil.isEmpty(crawlId)){
          System.out.println("Missing crawlID : crawl <seedDir> <crawlID> [<solrURL>] <numberOfRounds>");
      }

      if (StringUtil.isEmpty(solrUrl)){
          System.out.println("No SOLRURL specified. Skipping indexing.");
      }

      if (StringUtil.isEmpty(limit)){
          System.out.println("Missing numberOfRounds : crawl <seedDir> <crawlID> [<solrURL>] <numberOfRounds>");
      }
      //MODIFY THE PARAMETERS BELOW TO YOUR NEEDS
      //set the number of slaves nodes
        int numSlaves=1;
      //and the total number of available tasks
      //sets Hadoop parameter "mapred.reduce.tasks"
      int numTasks= numSlaves<<1;
      // number of urls to fetch in one iteration
      //250K per task?
//      int sizeFetchlist=numSlaves * 5;
      int sizeFetchlist=10;
      //time limit for feching
      String timeLimitFetch="180";
      //Adds <days> to the current time to facilitate
      //crawling urls already fetched sooner then
      //db.default.fetch.interval.
       int addDays=0;
    getConf().set("mapred.reduce.tasks", String.valueOf(numTasks));
    getConf().set("mapred.child.java.opts","-Xmx1000m");
    getConf().set("mapred.reduce.tasks.speculative.execution","false");
    getConf().set("mapred.map.tasks.speculative.execution","false");
    getConf().set("mapred.compress.map.output","true");
    InjectorJob injector = new InjectorJob(getConf());
    GeneratorJob generator = new GeneratorJob(getConf());
    FetcherJob fetcher = new FetcherJob(getConf());
    ParserJob parse = new ParserJob(getConf());
    DbUpdaterJob dbUpdaterJob = new DbUpdaterJob(getConf());
    IndexingJob  indexingJob = new IndexingJob();
    SolrDeleteDuplicates solrDeleteDuplicates = new SolrDeleteDuplicates();
    // initialize crawlDb
    getConf().set(Nutch.CRAWL_ID_KEY, crawlId);
      int res;
    String[]  injectParameter = new String[3];
    injectParameter[0] = seedDir;
    injectParameter[1] = "-crawlId";
    injectParameter[2] = crawlId;
    System.out.println("initial injection");
    res = ToolRunner.run(getConf(), injector,injectParameter);
    print(res,"inject");
    for (int i = 0; i < Integer.parseInt(limit); i++) {
     System.out.println("Begin Generate");
     String batchId = System.currentTimeMillis()+"-"+new Random().nextInt(32767);
        String[]  generateParameter = new String[10];
        // generate new segment
        generateParameter[0] = "-topN";
        generateParameter[1] = String.valueOf(sizeFetchlist);
        generateParameter[2] = "-noNorm";
        generateParameter[3] = "-noFilter";
        generateParameter[4] = "-adddays";
        generateParameter[5] = String.valueOf(addDays);
        generateParameter[6] = "-crawlId";
        generateParameter[7] = crawlId;
        generateParameter[8] = "-batchId";
        generateParameter[9] = batchId;
        res = ToolRunner.run(getConf(), generator,generateParameter);
        print(res,"generate");

        System.out.println("Begin Fetch");
        String[]  fetchParameter = new String[5];
        fetchParameter[0] = batchId;
        fetchParameter[1] = "-crawlId";
        fetchParameter[2] = crawlId;
        fetchParameter[3] = "-threads";
        //线程数量 thread
        fetchParameter[4] = "10";
        getConf().set("fetcher.timelimit.mins",timeLimitFetch);
        res = ToolRunner.run(getConf(),fetcher, fetchParameter);
        print(res,"fetch");
        /**
         * 配置文件中 已经在fetch过程中就使用parse 所以这个单独的parse不用在重复调用
         */
        System.out.println("parse begin");
        String[]  parseParameter = new String[3];
        parseParameter[0] = batchId;
        parseParameter[1] = "-crawlId";
        parseParameter[2] = crawlId;
        getConf().set("mapred.skip.attempts.to.start.skipping","2");
        getConf().set("mapred.skip.map.max.skip.records","1");
        res = ToolRunner.run(getConf(), parse,parseParameter);
        if (res==0){
            System.out.println("parse finish");
        }else {
            System.out.println("parse failed");
        }

        //updatedb with this batch
        System.out.println("begin updatedb");
        String[]  updatedbParameter = new String[3];
        updatedbParameter[0] = batchId;
        updatedbParameter[1] = "-crawlId";
        updatedbParameter[2] = crawlId;
        res = ToolRunner.run(getConf(),dbUpdaterJob,updatedbParameter);
        print(res,"updatedb");
        if (StringUtil.isEmpty(solrUrl)){
            System.out.println("Skipping indexing tasks: no SOLR url provided.");
        }else {
            System.out.println("begin Indexing");
            getConf().set("solr.server.url",solrUrl);
            String[] indexingParameter = new String[3];
            indexingParameter[0] = "-all";
            indexingParameter[1] = "-crawlId";
            indexingParameter[2] = crawlId;
            res = ToolRunner.run(getConf(), indexingJob, indexingParameter);
            print(res,"indexing");
            System.out.println("begin SOLR dedup");
            String[] solrdedupParameter = new String[1];
            solrdedupParameter[0] = solrUrl;
            res = ToolRunner.run(getConf(),solrDeleteDuplicates , solrdedupParameter);
            print(res,"solr Delete Duplicates");

        }
    }
      return 0;
  }

    public static void print(int res,String name ){
        if (res==0){
            System.out.println(name+" finish");
        }else if (res==1){
            System.out.println(name+" finish but no more URLs to fetch now,Escaping loop");
        }else {
            System.out.println(name+" failed");
        }
    }
}
