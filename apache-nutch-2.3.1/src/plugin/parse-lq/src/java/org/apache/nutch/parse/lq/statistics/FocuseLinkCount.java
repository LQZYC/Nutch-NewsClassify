package org.apache.nutch.parse.lq.statistics;

import org.apache.nutch.parse.lq.svm.SvmCheck;
import org.apache.nutch.parse.lq.svm.SvmResult;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * User 昶乐 Date 16/3/26 Time 17:15
 */
public class FocuseLinkCount {

    private AtomicLong                         atomicLongAllLink = new AtomicLong(0);
    private ConcurrentHashMap<Integer, LinkType> map               = new ConcurrentHashMap<>();
    private String linkDesc;

    public FocuseLinkCount(String linkDesc) {
        this.linkDesc = linkDesc;
    }
    public void addLinkCount(SvmResult svmResult){
        if (svmResult.getScore() < SvmCheck.THRESHOLD){
            atomicLongAllLink.incrementAndGet();
        }else {
            LinkType linkType = map.get(svmResult.getLabel());
            if (linkType == null){
                linkType = new LinkType(svmResult.getCategory(),svmResult.getLabel());
                map.put(svmResult.getLabel(),linkType);
            }
            linkType.increment();
        }
    }

    @Override public String toString() {
        StringBuilder stringBuffer = new StringBuilder("URL_HOST为:" + linkDesc + ";链接总数为:"+atomicLongAllLink.get()+"\n");
        for (Map.Entry entry :map.entrySet()){
            stringBuffer.append(entry.getValue().toString()).append("\n");
        }
        return stringBuffer.toString();
    }

    private class LinkType {

        private AtomicLong atomicLongFocuse = new AtomicLong(0);
        private String     labelDesc;
        private int     label;

        public LinkType(String labelDesc, int label){
            this.labelDesc = labelDesc;
            this.label = label;
        }

        @Override public String toString() {
            return "标签号:"+label+";类型:" + labelDesc + ";链接数量:" + atomicLongFocuse.toString()+";所占比例"+new BigDecimal(atomicLongFocuse.get()).divide(new BigDecimal(atomicLongAllLink.get()),2,BigDecimal.ROUND_HALF_UP);
        }

        public void increment() {
            atomicLongFocuse.incrementAndGet();
        }
    }
//    public static void main(String[] args) throws Exception {
//                 String localSrc = "/Users/lq-pc/project/FocuseNutch/apache-nutch-2.3.1/CHANGES.txt";
//                 String dst = "hdfs://120.25.162.238:9000/train/1.txt";
////                 InputStream in = new BufferedInputStream(new FileInputStream(localSrc));
////        StringInputStream st = new StringInputStream("中文");
//        InputStream in = org.apache.commons.io.IOUtils.toInputStream("中文","UTF-8");
//        Configuration conf = new Configuration();
//                 FileSystem fs = FileSystem.get(URI.create(dst), conf);
//                 OutputStream out = fs.create(new Path(dst), new Progressable() {
//                         public void progress() {
//                                 System.out.print(".");
//                             }
//                     });
//                 IOUtils.copyBytes(in, out, 4096, true);
//             }
}
