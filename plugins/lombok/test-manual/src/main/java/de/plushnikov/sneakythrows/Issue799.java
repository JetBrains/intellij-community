package de.plushnikov.sneakythrows;

import lombok.SneakyThrows;

import java.io.IOException;

public class Issue799 {

  public static void f() {
    Runnable runnable = () -> {
      throw new IOException("error");
    };
  }

  @SneakyThrows
  public static void g() {
    Runnable runnable = () -> {
      throw new IOException("error");
    };
  }
}
