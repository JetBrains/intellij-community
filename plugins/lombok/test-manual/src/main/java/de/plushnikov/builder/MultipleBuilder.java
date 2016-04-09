package de.plushnikov.builder;

import lombok.Builder;
import lombok.Data;

@Data
class Bean1 {
  private final int id;
  private final String string;
}

@Data
class Bean2 {
  private final int id;
  private final String string;
}

public class MultipleBuilder {

  private Bean1 bean1 = builder1().id(1).string("1").build();
  private Bean2 bean2 = builder2().id(2).string("2").build();

  @Builder(builderMethodName = "builder1")
  private static Bean1 createBean1(int id, String string) {
    return new Bean1(id, string);
  }

  @Builder(builderMethodName = "builder2")
  private static Bean2 createBean2(int id, String string) {
    return new Bean2(id, string);
  }

  public static void main(String[] args) {
    MultipleBuilder.builder1().id(1).build();
    MultipleBuilder.builder2().id(2).build();
  }
}