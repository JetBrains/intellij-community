package de.plushnikov.sneakythrows;

import lombok.SneakyThrows;

import java.io.IOException;

public class BugReport {
  private IInner inner;

  //@SneakyThrows(IOException.class)
  public BugReport() {
    inner = new IInner() {
      @SneakyThrows(IOException.class)
      @Override
      public IInner doSomething() {
        System.out.println();
        throw new IOException();
      }
    };
  }

  interface IInner {
    public IInner doSomething() throws IOException;
  }

  public static void main(String[] args) {
    System.out.println(new BugReport().toString());
  }
}