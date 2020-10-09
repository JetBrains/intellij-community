package de.plushnikov.superbuilder;

import lombok.experimental.SuperBuilder;

import java.util.List;

public class SuperBuilderWithGenerics {

  @SuperBuilder
  public static class Parent<A> {
    A field1;
    @lombok.Singular List<String> items;
  }

  @SuperBuilder
  public static class Child<A> extends Parent<A> {
    double field3;
    A fieldA;
  }

  public static void test() {
    Parent.ParentBuilder<String, ?, ?> parentBuilder = Parent.<String>builder();
    Parent.ParentBuilder<String, ?, ?> parentBuilder1 = parentBuilder.field1("Field1");
    Parent.ParentBuilder<String, ?, ?> parentBuilder2 = parentBuilder1.item("Item");
    Parent<String> parent = parentBuilder2.build();

    Parent<String> parent1 = Parent.<String>builder().field1("Field1").item("Item").build();

    Child.ChildBuilder<Integer, ?, ?> childBuilder = Child.<Integer>builder();
    Child.ChildBuilder<Integer, ?, ?> childBuilder1 = childBuilder.field3(0.0);
    Child.ChildBuilder<Integer, ?, ?> childBuilder2 = childBuilder1.field1(5);
    Child.ChildBuilder<Integer, ?, ?> childBuilder22 = childBuilder1.fieldA(5);
    Child.ChildBuilder<Integer, ?, ?> childBuilder3 = childBuilder2.item("");
    Child<Integer> x = childBuilder3.build();

    Child<Integer> x2 = Child.<Integer>builder().field3(0.0).field1(5).item("").build();
  }
}
