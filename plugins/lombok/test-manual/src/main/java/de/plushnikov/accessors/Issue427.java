package de.plushnikov.accessors;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(prefix = "m")
public class Issue427 {
  private String bar;

  private String mA;
  private int mB;

  public String getBar() {
    return bar;
  }

  public static void main(String[] args) {
    Issue427 foo = new Issue427();
    foo.setBar("bar");

    foo.mA = "aa";
    foo.getA();
    foo.setA("a");
    foo.setB(1);
    System.out.println(foo.getB() + 2);
  }
}

