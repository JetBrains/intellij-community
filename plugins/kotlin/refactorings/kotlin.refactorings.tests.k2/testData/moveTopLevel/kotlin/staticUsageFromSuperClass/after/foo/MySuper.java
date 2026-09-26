package foo;

public abstract class MySuper {
  public void foo() { }

  public static MySuper create() { return new MyImpl(); }
}
