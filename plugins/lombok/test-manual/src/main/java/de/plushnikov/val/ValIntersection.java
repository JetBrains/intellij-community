package de.plushnikov.val;

import lombok.val;

public class ValIntersection {
  public static void main(String[] args) {
    int a = 4;
    a++;
    int b = 0b101;
    val test = a == b ? 5 : "";
    System.out.println(test.toString());
  }
}
