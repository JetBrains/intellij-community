package de.plushnikov.synchronised;

import lombok.Synchronized;

public abstract class SynchronizedBean {

  private final Object fooLock = new Object();
  private final Object readLock = new Object();

  public static void hello() {
    System.out.println("world");
  }

  @Synchronized
  public int answerToLife() {
    return 42;
  }

  @Synchronized("fooLock")
  public void foo2() {
    System.out.println("foo2sdfsdf");
  }

  @Synchronized("readLock")
  public void foo() {
    System.out.println("bar2dfsdfdsf");
  }

  @Synchronized
  public int getSomething() {
    return 0;
  }
}
