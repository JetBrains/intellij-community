package de.plushnikov.data;

import lombok.Data;

import java.util.Date;

@Data
public class DataGetter {
  private Integer Article;

  private Date getArticle(Date date) {
    System.out.println("getArticle(Date date)");
    return new Date();
  }

  private Date getArticle(Date... date) {
    System.out.println("getArticle(Date ... date)");
    return new Date();
  }

  public static void main(String[] args) {
    DataGetter test = new DataGetter();
    test.getArticle();
    test.getArticle(new Date());
  }
}
