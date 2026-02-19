public class EqualsAndHashCodeCallSuper {

  @lombok.EqualsAndHashCode
  private static class First {
    private String field1;
  }

  <warning descr="Generating equals/hashCode implementation but without a call to superclass, even though this class does not extend java.lang.Object. If this is intentional, add '(callSuper=false)' to your type.">@lombok.EqualsAndHashCode</warning>
  private static class Second1 extends First {
    private String field2;
  }

  @lombok.EqualsAndHashCode(callSuper = false)
  private static class Second2 extends First {
    private String field2;
  }

  @lombok.EqualsAndHashCode(callSuper = true)
  private static class Second3 extends First {
    private String field2;
  }

///////////////////Data///////////////////////////

  <warning descr="Generating equals/hashCode implementation but without a call to superclass, even though this class does not extend java.lang.Object. If this is intentional, add '(callSuper=false)' to your type.">@lombok.Data</warning>
  private static class Data1 extends First {
    private String field2;
  }

  @lombok.Data
  <warning descr="Generating equals/hashCode implementation but without a call to superclass, even though this class does not extend java.lang.Object. If this is intentional, add '(callSuper=false)' to your type.">@lombok.EqualsAndHashCode</warning>
  private static class Data2 extends First {
    private String field2;
  }

  @lombok.Data
  @lombok.EqualsAndHashCode(callSuper = true)
  private static class Data3 extends First {
    private String field2;
  }

  @lombok.Data
  @lombok.EqualsAndHashCode(callSuper = false)
  private static class Data4 extends First {
    private String field2;
  }

///////////////////Value///////////////////////////

  <warning descr="Generating equals/hashCode implementation but without a call to superclass, even though this class does not extend java.lang.Object. If this is intentional, add '(callSuper=false)' to your type.">@lombok.Value</warning>
  private static class Value1 extends First {
    private String field2;
  }

  @lombok.Value
  <warning descr="Generating equals/hashCode implementation but without a call to superclass, even though this class does not extend java.lang.Object. If this is intentional, add '(callSuper=false)' to your type.">@lombok.EqualsAndHashCode</warning>
  private static class Value2 extends First {
    private String field2;
  }

  @lombok.Value
  @lombok.EqualsAndHashCode(callSuper = true)
  private static class Value3 extends First {
    private String field2;
  }

  @lombok.Value
  @lombok.EqualsAndHashCode(callSuper = false)
  private static class Value4 extends First {
    private String field2;
  }
}
