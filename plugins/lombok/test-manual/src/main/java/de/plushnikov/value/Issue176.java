package de.plushnikov.value;

import lombok.Value;

@Value(staticConstructor = "of")
public class Issue176<T> {
  private T name;
  private int count;

  public static void main(String[] args) {
    Issue176<String> valueObject = Issue176.of("thing1", 10);
    System.out.println(valueObject);
  }
}
