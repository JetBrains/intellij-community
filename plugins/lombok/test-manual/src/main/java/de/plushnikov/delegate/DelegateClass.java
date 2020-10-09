package de.plushnikov.delegate;

import lombok.experimental.Delegate;

import java.util.ArrayList;
import java.util.Collection;

public class DelegateClass {
  private interface SimpleCollection {
    boolean add(String item);

    boolean remove(Object item);
  }

  private interface AnotherSimpleCollection {
    boolean add2(String item);

    boolean remove(Object item);
  }

  private interface AnotherSimpleCollection2 {
    boolean add3(String item);

    boolean remove(Object item);
  }

  @Delegate(excludes = {AnotherSimpleCollection.class, AnotherSimpleCollection2.class})
  private final Collection<String> collection = new ArrayList<String>();

  public static void main(String[] args) {
    DelegateClass test = new DelegateClass();
    test.add("dgdh");
  }
}
