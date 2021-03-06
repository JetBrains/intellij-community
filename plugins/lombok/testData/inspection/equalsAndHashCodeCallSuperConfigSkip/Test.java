public class Test {

  @lombok.EqualsAndHashCode
  private static class First {
    private String field1;
  }

  @lombok.EqualsAndHashCode
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

  @lombok.Data
  private static class Data1 extends First {
    private String field2;
  }

  @lombok.Data
  @lombok.EqualsAndHashCode
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

  @lombok.Value
  private static class Value1 extends First {
    private String field2;
  }

  @lombok.Value
  @lombok.EqualsAndHashCode
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
