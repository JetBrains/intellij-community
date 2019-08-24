package de.plushnikov.superbuilder;

import java.util.List;

public class SuperBuilderSimple {

	public static class Parent {
		int field1;
		List<String> items;

    protected Parent(ParentBuilder b) {
    }

    public static ParentBuilderImpl builder() {
      return new ParentBuilderImpl();
    }

    public static class ParentBuilder {
    }

    public static class ParentBuilderImpl {
    }
  }

	public static class Child extends Parent {
		double field3;

    protected Child(ChildBuilder b) {
    }

    public static ChildBuilderImpl builder() {
      return new ChildBuilderImpl();
    }

    public static class ChildBuilder {
    }

    public static class ChildBuilderImpl {
    }
  }

	public static void test() {
		Child x = Child.builder().field3(0.0).field1(5).item("").build();
	}
}
