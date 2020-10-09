package de.plushnikov.accessors;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

@Accessors(prefix = "m")
@Builder
@Getter
public class Example151 {

  private final String mAField;

  public static void main(String[] args) {
    System.out.println(builder().aField("a").build().getAField());
  }
}
