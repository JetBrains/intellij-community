import lombok.Data;
import lombok.EqualsAndHashCode;

public class DataInheritanceHighlighting {

  @Data
  @EqualsAndHashCode(callSuper = true)
  static class WithConstructor extends ParentWithNonDefaultConstructor {
    private String field;

    // Explicitly define constructor to call parent constructor
    WithConstructor() {
      super("defaultValue");
    }
  }

  <error descr="Lombok needs a default constructor in the base class">@Data</error>
  @EqualsAndHashCode(callSuper = true)
  static class WithoutConstructor extends ParentWithNonDefaultConstructor {
    private String field;
    // No constructor defined - should show error
  }

  static class ParentWithNonDefaultConstructor {
    private final String requiredField;

    ParentWithNonDefaultConstructor(String requiredField) {
      this.requiredField = requiredField;
    }
  }
}