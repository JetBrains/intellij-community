package de.plushnikov.refactor;

import lombok.Getter;

public class Issue155 {
  @Getter
  String foo;

  public void beforeRefactoring() {
    String testFoo = this.getFoo();
  }

  public void afterRefactoring() {
    String testFoo = this.getFoo();
  }
}
