import lombok.Data;

public class DataEqualsAndHashCodeOverride {
  @Data
  private static class SomeBasic {
    private int property;
  }

  @Data
  private static class SomesComplex extends SomeBasic {
    private final String value;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      SomesComplex that = (SomesComplex) o;

      return value.equals(that.value);
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }
  }

  <warning descr="Generating equals/hashCode implementation but without a call to superclass, even though this class does not extend java.lang.Object. If this is intentional, add '(callSuper=false)' to your type.">@Data</warning>
  private static class SomeDifferent extends SomeBasic {
    private final String value;
  }

  public static void main(String[] args) {
    SomesComplex complex = new SomesComplex("This is very complex");
    System.out.println(complex);

    SomeDifferent different = new SomeDifferent("This is something different");
    System.out.println(different);
  }
}
