package org.apache.nutch.parse.lq.svm;

/**
 * User 昶乐
 * Date 16/3/23
 * Time 09:49
 */
public class SvmResult {
  private double score;
  private String category;
  private boolean isExceedThreshold;
  private int label;
    public SvmResult(String category, double score,boolean isExceedThreshold,int label) {
        this.category = category;
        this.score = score;
        this.isExceedThreshold = isExceedThreshold;
        this.label = label;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isExceedThreshold() {
        return isExceedThreshold;
    }

    public void setExceedThreshold(boolean exceedThreshold) {
        isExceedThreshold = exceedThreshold;
    }

    public int getLabel() {
        return label;
    }

    public void setLabel(int label) {
        this.label = label;
    }
}
