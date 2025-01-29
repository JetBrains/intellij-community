package extension;

import java.io.IOException;

public class CheckedExceptionQuickFixExample {

  public int calcSomething() {
      <caret>throw new IOException();
  }

  public static void main(String[] args) {
    System.out.println(new CheckedExceptionQuickFixExample().calcSomething());
  }
}
