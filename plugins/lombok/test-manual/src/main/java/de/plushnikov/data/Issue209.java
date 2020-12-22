package de.plushnikov.data;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
class Issue209Parent {
  private int abc;
}

@Data
@EqualsAndHashCode(callSuper = false)
public class Issue209 extends Issue209Parent {
  private String xyz;

  public static void main(String[] args) {
    System.out.println(new Issue209());
  }
}
