package de.plushnikov.intellij.plugin.configsystem;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class NoArgsConstructorTest {

  private String someProperty;

  public static void main(String[] args) {
    final NoArgsConstructorTest test = new NoArgsConstructorTest();
    System.out.println(test);
  }
}
