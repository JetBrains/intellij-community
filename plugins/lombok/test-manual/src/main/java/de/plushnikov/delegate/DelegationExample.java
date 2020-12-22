package de.plushnikov.delegate;

import lombok.experimental.Delegate;

import java.util.ArrayList;
import java.util.Collection;

public class DelegationExample {
  private interface SimpleCollection {
    boolean add(String item);

    boolean remove(Object item);
  }

  @Delegate(types = SimpleCollection.class)
  private final Collection<String> collection = new ArrayList<String>();

  public static void main(String[] args) {
    DelegationExample example = new DelegationExample();
    example.add("Hello World");
  }
}

class ExcludesDelegateExample {
  long counter = 0L;

  private interface Add {
    boolean add(String x);

    boolean addAll(Collection<? extends String> col);
  }

  @Delegate(excludes = Add.class)
  private final Collection<String> collection = new ArrayList<String>();

  public boolean add(String item) {
    counter++;
    return collection.add(item);
  }

  public boolean addAll(Collection<? extends String> col) {
    counter += col.size();
    return collection.addAll(col);
  }
}
