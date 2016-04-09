package de.plushnikov.sneakythrows;

import lombok.SneakyThrows;

class SneakyThrowsPlain {
  @SneakyThrows
  SneakyThrowsPlain() {
    super();
    System.out.println("constructor");
  }

  @SneakyThrows
  SneakyThrowsPlain(int x) {
    this();
    System.out.println("constructor2");
  }

  @SneakyThrows
  public void test() {
    System.out.println("test1");
  }

  @SneakyThrows
  public void test2() {
    System.out.println("test2");
  }
}