package de.plushnikov.delegate.issue76;

public class TypeA {
  public int doStuff(String str) {
    return Integer.parseInt(str);
  }

  public static void main(String[] args) {
    System.out.println(new TypeA().doStuff("8988"));
  }
}
