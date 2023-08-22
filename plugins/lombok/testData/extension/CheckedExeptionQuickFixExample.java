package extension;

import java.io.IOException;

public class CheckedExeptionQuickFixExample {

  public int calcSomething() {
      <caret>throw new IOException();
  }

  public static void main(String[] args) {
    System.out.println(new CheckedExeptionQuickFixExample().calcSomething());
  }
}
