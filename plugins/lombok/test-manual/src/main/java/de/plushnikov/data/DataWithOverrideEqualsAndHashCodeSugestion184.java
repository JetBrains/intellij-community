package de.plushnikov.data;

import lombok.Data;

public class DataWithOverrideEqualsAndHashCodeSugestion184 {
  @Data
  private static class SomeBasic {
    private int property;
  }

  @Data
  private static class SomeComplex extends SomeBasic {
    private final String value;

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      SomeComplex that = (SomeComplex) o;

      return value.equals(that.value);
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }
  }

  @Data
  private static class SomeDifferent extends SomeBasic {
    private final String value;
  }

  public static void main(String[] args) {
    SomeComplex complex = new SomeComplex("This is very complex");
    System.out.println(complex);

    SomeDifferent different = new SomeDifferent("This is something different");
    System.out.println(different);
  }
}
