import java.util.List;

final class WithAndBuilderDefaultOnFieldAndValueOnClass {

  private final String field1;
  private final List<String> field2 = List.of();// TODO remove initializer for Builder.Default

  public static void main(String[] args) {
    WithAndBuilderDefaultOnFieldAndValueOnClass build = builder().field1("1").field2(List.of("2")).build();
    WithAndBuilderDefaultOnFieldAndValueOnClass field2 = build.withField2(List.of("3"));
    System.out.println(field2);
  }

  private static List<String> $default$field2() {
    return List.of();
  }

  WithAndBuilderDefaultOnFieldAndValueOnClass(String field1, List<String> field2) {
    this.field1 = field1;
    this.field2 = field2;
  }

  public static WithAndBuilderDefaultOnFieldAndValueOnClass.WithAndBuilderDefaultOnFieldAndValueOnClassBuilder builder() {
    return new WithAndBuilderDefaultOnFieldAndValueOnClass.WithAndBuilderDefaultOnFieldAndValueOnClassBuilder();
  }

  public String getField1() {
    return this.field1;
  }

  public List<String> getField2() {
    return this.field2;
  }

  public boolean equals(Object o) {
    if (o == this) {
      return true;
    } else if (!(o instanceof WithAndBuilderDefaultOnFieldAndValueOnClass)) {
      return false;
    } else {
      WithAndBuilderDefaultOnFieldAndValueOnClass other = (WithAndBuilderDefaultOnFieldAndValueOnClass)o;
      Object this$field1 = this.getField1();
      Object other$field1 = other.getField1();
      if (this$field1 == null) {
        if (other$field1 != null) {
          return false;
        }
      } else if (!this$field1.equals(other$field1)) {
        return false;
      }

      Object this$field2 = this.getField2();
      Object other$field2 = other.getField2();
      if (this$field2 == null) {
        if (other$field2 != null) {
          return false;
        }
      } else if (!this$field2.equals(other$field2)) {
        return false;
      }

      return true;
    }
  }

  public int hashCode() {
    int PRIME = true;
    int result = 1;
    Object $field1 = this.getField1();
    int result = result * 59 + ($field1 == null ? 43 : $field1.hashCode());
    Object $field2 = this.getField2();
    result = result * 59 + ($field2 == null ? 43 : $field2.hashCode());
    return result;
  }

  public String toString() {
    String var10000 = this.getField1();
    return "WithAndBuilderDefaultOnFieldAndValueOnClass(field1=" + var10000 + ", field2=" + this.getField2() + ")";
  }

  public WithAndBuilderDefaultOnFieldAndValueOnClass withField2(List<String> field2) {
    return this.field2 == field2 ? this : new WithAndBuilderDefaultOnFieldAndValueOnClass(this.field1, field2);
  }

  public static class WithAndBuilderDefaultOnFieldAndValueOnClassBuilder {
    private String field1;
    private boolean field2$set;
    private List<String> field2$value;

    WithAndBuilderDefaultOnFieldAndValueOnClassBuilder() {
    }

    public WithAndBuilderDefaultOnFieldAndValueOnClass.WithAndBuilderDefaultOnFieldAndValueOnClassBuilder field1(String field1) {
      this.field1 = field1;
      return this;
    }

    public WithAndBuilderDefaultOnFieldAndValueOnClass.WithAndBuilderDefaultOnFieldAndValueOnClassBuilder field2(List<String> field2) {
      this.field2$value = field2;
      this.field2$set = true;
      return this;
    }

    public WithAndBuilderDefaultOnFieldAndValueOnClass build() {
      List<String> field2$value = this.field2$value;
      if (!this.field2$set) {
        field2$value = WithAndBuilderDefaultOnFieldAndValueOnClass.$default$field2();
      }

      return new WithAndBuilderDefaultOnFieldAndValueOnClass(this.field1, field2$value);
    }

    public String toString() {
      return "WithAndBuilderDefaultOnFieldAndValueOnClass.WithAndBuilderDefaultOnFieldAndValueOnClassBuilder(field1=" + this.field1 + ", field2$value=" + this.field2$value + ")";
    }
  }
}
