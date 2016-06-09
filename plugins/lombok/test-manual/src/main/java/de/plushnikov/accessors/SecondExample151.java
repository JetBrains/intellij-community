package de.plushnikov.accessors;

import lombok.Builder;
import lombok.ToString;
import lombok.experimental.Accessors;

@Accessors(prefix = "m")
@Builder
@ToString
public class SecondExample151 {

  public static class SecondExample151Builder {
    private String field = "default";
  }

  private final String mField;
  private final String mField2;
  private final String mField3;

  public static void main(String[] args) {
    System.out.println(builder().field("abcd").build());
  }
}
