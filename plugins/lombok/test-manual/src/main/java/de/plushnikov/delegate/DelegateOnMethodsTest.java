package de.plushnikov.delegate;

import lombok.experimental.Delegate;

import java.util.ArrayList;

public class DelegateOnMethodsTest extends DelegateOnMethods {
  @Override
  public Bar getBar() {
    return new Bar() {
      @Override
      public void bar(ArrayList<String> list) {
        list.add("");
      }
    };
  }

  public static void main(String[] args) {
    DelegateOnMethodsTest delegateOnMethods = new DelegateOnMethodsTest();
    delegateOnMethods.getBar();
    delegateOnMethods.bar(new ArrayList<String>());
  }
}

abstract class DelegateOnMethods {
  @Delegate
  public abstract Bar getBar();

  public static interface Bar {
    void bar(java.util.ArrayList<java.lang.String> list);
  }
}
