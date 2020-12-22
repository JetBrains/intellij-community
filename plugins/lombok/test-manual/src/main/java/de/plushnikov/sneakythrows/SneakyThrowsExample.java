package de.plushnikov.sneakythrows;

import lombok.SneakyThrows;

import java.io.UnsupportedEncodingException;

public class SneakyThrowsExample implements Runnable {

  @SneakyThrows({UnsatisfiedLinkError.class, UnsupportedEncodingException.class, IllegalAccessException.class})
  public String utf8ToString(byte[] bytes) {
    if (1 == 1) {
      return new String(bytes, "UTgF-8");
    } else {
      throw new MyException();
    }
  }

  public class MyException extends IllegalAccessException {

  }

  @SneakyThrows
  public void run() {
    throw new Throwable();
  }

  public static void main(String[] args) throws IllegalAccessException {
    SneakyThrowsExample example = new SneakyThrowsExample();

    System.out.println(example.utf8ToString("Test".getBytes()));

//        val s = 12;

    example.run();
  }
}
