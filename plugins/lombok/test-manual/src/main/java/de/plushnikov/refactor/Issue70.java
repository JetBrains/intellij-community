package de.plushnikov.refactor;

import lombok.Data;

@Data
public class Issue70 {
  private String bar;

  public static void main(String[] args) {
    Issue70 foo = new Issue70();
    foo.setBar("bar");
    System.out.println(foo);
  }
}
