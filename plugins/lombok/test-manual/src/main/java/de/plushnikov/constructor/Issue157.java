package de.plushnikov.constructor;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UnknownFormatConversionException;

public class Issue157 {
  @RequiredArgsConstructor(staticName = "of")
  public static class Foo<T, E extends Exception> {
    private final Map<T, E> bar;

    public Map<T, E> buildBar() {
      // ...
      return bar;
    }
  }

  public static void main(String[] args) {
    Foo<String, UnknownFormatConversionException> foo = new Foo<>(new HashMap<String, UnknownFormatConversionException>());
    System.out.println(foo);

    HashMap<Integer, IOException> hashMap = new HashMap<>();
    Foo<Integer, IOException> myFoo = Foo.of(hashMap);
    Map<Integer, IOException> bar = myFoo.buildBar();
    System.out.println(bar);

    Foo<Integer, NullPointerException> exceptionFoo = Foo.of(new HashMap<Integer, NullPointerException>());
    System.out.println(exceptionFoo.buildBar());
  }
}