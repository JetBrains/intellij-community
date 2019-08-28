package de.plushnikov.superbuilder;

import java.util.List;

public class SuperBuilderWithGenerics {
  @lombok.experimental.SuperBuilder
  public static class Parent<A> {
    A field1;
    @lombok.Singular List<String> items;
  }

  @lombok.experimental.SuperBuilder
  public static class Child<A> extends Parent<A> {
    double field3;
  }

  public static void test() {
    Child.ChildBuilder<Integer, ?, ?> childBuilder = Child.<Integer>builder();
    Child.ChildBuilder<Integer, ?, ?> childBuilder1 = childBuilder.field3(0.0);
    Child.ChildBuilder<Integer, ?, ?> childBuilder2 = childBuilder1.field1(5);
    Child.ChildBuilder<Integer, ?, ?> childBuilder3 = childBuilder2.item("");
    Child<Integer> x = childBuilder3.build();

    Child<Integer> x2 = Child.<Integer>builder().field3(0.0).field1(5).item("").build();
  }
}
