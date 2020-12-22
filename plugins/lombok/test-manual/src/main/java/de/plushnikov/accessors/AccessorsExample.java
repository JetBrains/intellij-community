package de.plushnikov.accessors;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
public class AccessorsExample {

  @Getter
  @Setter
  private int age = 10;

  static class PrefixExample {
    @Accessors(prefix = "f")
    @Getter
    private String fName = "Hello, World!";
  }

  @lombok.experimental.Accessors(fluent = true, chain = true, prefix = {"f", ""})
  @lombok.Setter
  @lombok.Getter
  static class AccessorsPrefix {
    private String fieldName;
    private String fActualField;
    private String fSUPERField;
  }


  public static void main(String[] args) {
    AccessorsExample accessorsExample = new AccessorsExample();
    System.out.println(accessorsExample.age(100).age());

    PrefixExample prefixExample = new PrefixExample();
    System.out.println(prefixExample.getName());

    AccessorsPrefix accessorsPrefix = new AccessorsPrefix();
    accessorsPrefix.fieldName();
    accessorsPrefix.fieldName("sss");
    accessorsPrefix.actualField("aaaaa").actualField();
    accessorsPrefix.sUPERField("aaaaa").sUPERField();
  }
}
