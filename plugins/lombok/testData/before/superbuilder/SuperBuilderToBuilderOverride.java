import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true)
class Base {
  private String string;
}


@Getter
@SuperBuilder(toBuilder = true)
class Sub extends Base {
  private int number;

  protected Sub(SubBuilder<?, ?> builder) {
    super(builder);
  }

  public SubBuilder<?, ?> toBuilder() {
    // custom code should go here
    return new SubBuilderImpl().$fillValuesFrom(this);
  }
}
