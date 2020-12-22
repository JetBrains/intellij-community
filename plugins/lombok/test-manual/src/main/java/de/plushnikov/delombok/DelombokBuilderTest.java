package de.plushnikov.delombok;

import lombok.Builder;

public class DelombokBuilderTest {
  private Bean bean = builder().id(1).string("1").build();

  @Builder
  private static Bean createBean(int id, String string) {
    return new Bean(id, string);
  }
}