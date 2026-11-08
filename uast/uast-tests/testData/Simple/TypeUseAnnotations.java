package test.pkg;

@java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE)
public @interface A {}

public class Foo {
  public @A int foo1() {}
  public @A String foo2() {}
  public @A <T> T foo3() {}
}