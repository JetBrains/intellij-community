package de.plushnikov.delegate;

import lombok.experimental.Delegate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DelegateWithGenerics {
  @Delegate
  private final List<String> list = new ArrayList<String>();

  public static void main(String[] args) {
    DelegateWithGenerics withGenerics = new DelegateWithGenerics();
    withGenerics.addAll(Arrays.asList("x", "y"));
    withGenerics.add("z");
    System.out.println(withGenerics);
  }
}
