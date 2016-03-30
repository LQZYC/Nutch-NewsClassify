package org.apache.nutch.parse.lq.svm;
import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.util.NutchConfiguration;
import org.apache.nutch.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thunlp.text.classifiers.BasicTextClassifier;
import org.thunlp.text.classifiers.ClassifyResult;
import org.thunlp.text.classifiers.LinearBigramChineseTextClassifier;

import java.io.InputStreamReader;
/**
 *  昶乐
 */
public class SvmCheck {
    private static final String SVM_MODEL_LEXICON = "svm.model.lexicon";
    private static final String SVM_MODEL         = "svm.model";
    private static final String SVM_CATEGORY      = "svm.category";
    public static final double THRESHOLD         = 0.5;
    public static final  Logger LOG               = LoggerFactory.getLogger(SvmCheck.class);
    private   BasicTextClassifier classifier;
    private synchronized  BasicTextClassifier getClassifier(Configuration conf) throws Exception {
        if (classifier != null){
            return classifier;
        }
        String svmModelLexicon = conf.get(SVM_MODEL_LEXICON);
        String svmModel = conf.get(SVM_MODEL);
        String svmCategory = conf.get(SVM_CATEGORY);
        if (svmModelLexicon == null||svmCategory == null) {
            LOG.error("读取模型文件失败");
            return null;
        }
        // 新建分类器对象
        BasicTextClassifier classifier = new BasicTextClassifier();
        classifier.setConf(conf);
        // 设置分类种类，并读取模型
        classifier.loadCategoryListFromFile((InputStreamReader)conf.getConfResourceAsReader(svmCategory));
        classifier.setTextClassifier(new LinearBigramChineseTextClassifier(classifier.getCategorySize()));
        //classifier.getTextClassifier().loadModel("/home/lq/apache-nutch-2.3.1/conf/svmmodel");

        classifier.getTextClassifier().loadModel(conf,svmModelLexicon,svmModel);
        this.classifier = classifier;
        return classifier;
    }

	
	/**
	 * 如果需要读取已经训练好的模型，再用其进行分类，可以按照本函数的代码调用分类器
	 * 
	 */
	public SvmResult runLoadModelAndUse(Configuration conf, String text ) throws Exception {
        if (StringUtil.isEmpty(text)){
            return null;
        }
		ClassifyResult[] result = getClassifier(conf).classifyText(text, 1);
        return (result[0].prob) < THRESHOLD ? new SvmResult(classifier.getCategoryName(result[0].label),result[0].prob,false,result[0].label) : new SvmResult(classifier.getCategoryName(result[0].label),result[0].prob,true,result[0].label);
	}
        public static void main(String args[]) throws Exception {
            Configuration conf = NutchConfiguration.create();
            System.out.println(new SvmCheck().runLoadModelAndUse(conf,"NBA-正直播科比LBJ告别战与KB谢幕?"));
        }
}
