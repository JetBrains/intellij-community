import lombok.Builder;
import lombok.experimental.SuperBuilder;

@Builder
class Bar {
  @Builder.Default
  private final String bar = "FooBar";

  private final String foo = "Foo";

  public Bar(String bar, String foo) {
    this.bar = bar;
    <error descr="Cannot assign a value to final variable 'foo'">this.foo</error> = foo;
  }
}

@SuperBuilder
class Foo {
  @Builder.Default
  private final String bar = "FooBar";

  private final String foo = "Foo";

  public Foo(String bar, String foo) {
    this.bar = bar;
    <error descr="Cannot assign a value to final variable 'foo'">this.foo</error> = foo;
  }
}

public class BuilderWithDefaultReinitializeInConstructor {
  public static void main(String[] args) {
    new Bar("FooBar", "Foo");
    new Foo("FooBar", "Foo");
  }
}