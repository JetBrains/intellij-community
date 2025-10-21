import lombok.Value;
import lombok.EqualsAndHashCode;

public class ValueInheritanceHighlighting {

  @Value
  @EqualsAndHashCode(callSuper = true)
  static class WithConstructor extends ParentWithNonDefaultConstructor {
    String field;

    // Explicitly define constructor to call parent constructor
    WithConstructor(String field) {
      super("defaultValue");
      this.field = field;
    }
  }

  <error descr="Lombok needs a default constructor in the base class">@Value</error>
  @EqualsAndHashCode(callSuper = true)
  static class WithoutConstructor extends ParentWithNonDefaultConstructor {
    String field;
    // No constructor defined - should show error
  }

  static class ParentWithNonDefaultConstructor {
    private final String requiredField;

    ParentWithNonDefaultConstructor(String requiredField) {
      this.requiredField = requiredField;
    }
  }
}