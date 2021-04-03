import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldNameConstants;

public class FieldNameConstantsExample {

  @Getter
  @AllArgsConstructor
  @FieldNameConstants
  public static class Foo {
    private String bar;
    private String foo;
  }

  public void logFieldValue(Foo foo, String fieldName) {
    switch (fieldName) {
      //should not produce error "Constant expression required" > Foo.Fields.bar
      case Foo.Fields.bar:
        System.out.printf("Field %s contains: %s", fieldName, foo.getBar());
        break;
      //should not produce error "Constant expression required" > Foo.Fields.foo
      case Foo.Fields.foo:
        System.out.printf("Field %s contains: %s", fieldName, foo.getFoo());
        break;
      default:
        System.out.printf("Foo class doesn't have field: %s", fieldName);
    }
  }

  public static void main(String[] args) {
    new FieldNameConstantsExample().logFieldValue(new Foo("a", "b"), "foo");
  }
}
