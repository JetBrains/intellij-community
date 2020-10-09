package de.plushnikov.var;

import lombok.experimental.var;

import java.util.ArrayList;

public class VarExample {
  public String example() {
    var example = new ArrayList<String>();
    example.add("Hello, World!");
    final var foo = example.get(0);
    return foo.toLowerCase();
  }

  public void example2() {
    var list = new ArrayList<String>();
    list = new ArrayList<>();
    list.add("zero");
    list.add("one");
    list.add("two");
    for (var i = 0; i < list.size(); ++i) {
      System.out.printf("%d: %s\n", i, list.get(i));
    }
  }
}
