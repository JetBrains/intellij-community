package de.plushnikov.delegate;

import lombok.experimental.Delegate;

interface SomeI {
  float makeFloat();
}

class SomeB implements SomeI {
  public float makeFloat() {
    return 1.0f;
  }
}

class SomeA extends SomeB {
  public int makeInt() {
    return 1;
  }
}

public class DelegateInheritence {

  @Delegate
  private SomeA myDelegator = new SomeA();

  //    @Delegate
  public SomeA getSomeA() {
    return myDelegator;
  }

  public static void main(String[] args) {
    DelegateInheritence test = new DelegateInheritence();
    System.out.println(test.makeFloat());
    System.out.println(test.makeInt());
  }
}
