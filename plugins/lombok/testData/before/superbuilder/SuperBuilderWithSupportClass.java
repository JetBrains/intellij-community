import lombok.NonNull;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public class SuperBuilderWithSupportClass {

  public static SuperBuilderWithSupportClassBuilder<?, ?> builder() {
    return new SuperBuilderWithSupportClassBuilderImpl();
  }

  public static SuperBuilderWithSupportClassBuilder<?, ?> builder(@NonNull final SupportClass supportClass) {
    return new SuperBuilderWithSupportClassBuilderImpl(supportClass);
  }

  public abstract static class SuperBuilderWithSupportClassBuilder<
    C extends SuperBuilderWithSupportClass, B extends SuperBuilderWithSupportClassBuilder<C, B>> {

    private SupportClass supportClass;

    public SuperBuilderWithSupportClassBuilder() {
      this(new SupportClass());
    }

    public SuperBuilderWithSupportClassBuilder(final SupportClass supportClass) {
      this.supportClass = supportClass;
    }
  }

  private static final class SuperBuilderWithSupportClassBuilderImpl
    extends SuperBuilderWithSupportClass.SuperBuilderWithSupportClassBuilder<SuperBuilderWithSupportClass, SuperBuilderWithSupportClass.SuperBuilderWithSupportClassBuilderImpl> {

    private SuperBuilderWithSupportClassBuilderImpl(@NonNull final SupportClass supportClass) {
      super(supportClass);
    }
  }

  public static class SupportClass {

  }
}