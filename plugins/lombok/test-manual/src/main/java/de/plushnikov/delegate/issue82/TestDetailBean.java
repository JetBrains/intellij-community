package de.plushnikov.delegate.issue82;

import lombok.experimental.Delegate;

//TODO fix it
public class TestDetailBean //extends AbstractDetailBean<Integer>
{
  @Delegate
  private MyDetailData detailData;

  public static void main(String[] args) {
    TestDetailBean bean = new TestDetailBean();

    //bean.getResult();
    bean.getSomething();
  }
}