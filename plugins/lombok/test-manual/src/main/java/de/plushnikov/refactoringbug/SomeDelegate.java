package de.plushnikov.refactoringbug;

import lombok.experimental.Delegate;

public class SomeDelegate {
  @Delegate
  private SomeApi someApi = new SomeApiImpl();

  public static void main(String[] args) {
    SomeDelegate delegate = new SomeDelegate();
    System.out.println(delegate.makeSomething(10));
  }
}
