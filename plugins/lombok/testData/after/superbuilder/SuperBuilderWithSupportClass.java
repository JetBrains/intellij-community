import lombok.NonNull;

public class SuperBuilderWithSupportClass {

  @java.lang.SuppressWarnings("all")
  public abstract static class SuperBuilderWithSupportClassBuilder<
    C extends SuperBuilderWithSupportClass, B extends SuperBuilderWithSupportClassBuilder<C, B>> {

    @java.lang.SuppressWarnings("all")
    private SupportClass supportClass;

    @java.lang.SuppressWarnings("all")
    public SuperBuilderWithSupportClassBuilder() {
      this(new SupportClass());
    }

    @java.lang.SuppressWarnings("all")
    public SuperBuilderWithSupportClassBuilder(final SupportClass supportClass) {
      this.supportClass = supportClass;
    }

    @java.lang.SuppressWarnings("all")
    protected abstract B self();

    @java.lang.SuppressWarnings("all")
    public abstract C build();

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
      return "SuperBuilderWithSupportClass.SuperBuilderWithSupportClassBuilder(supportClass=" + this.supportClass + ")";
    }
  }

  @java.lang.SuppressWarnings("all")
  private static final class SuperBuilderWithSupportClassBuilderImpl
    extends SuperBuilderWithSupportClass.SuperBuilderWithSupportClassBuilder<SuperBuilderWithSupportClass, SuperBuilderWithSupportClass.SuperBuilderWithSupportClassBuilderImpl> {

    @java.lang.SuppressWarnings("all")
    private SuperBuilderWithSupportClassBuilderImpl() {

    }

    @java.lang.SuppressWarnings("all")
    private SuperBuilderWithSupportClassBuilderImpl(@NonNull final SupportClass supportClass) {
      super(supportClass);
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    protected SuperBuilderWithSupportClassBuilderImpl self() {
      return this;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public SuperBuilderWithSupportClass build() {
      return new SuperBuilderWithSupportClass(this);
    }
  }

  @java.lang.SuppressWarnings("all")
  protected SuperBuilderWithSupportClass(SuperBuilderWithSupportClassBuilder<?, ?> b) {
  }

  public static class SupportClass {

  }

  @java.lang.SuppressWarnings("all")
  public static SuperBuilderWithSupportClassBuilder<?, ?> builder() {
    return new SuperBuilderWithSupportClassBuilderImpl();
  }

  @java.lang.SuppressWarnings("all")
  public static SuperBuilderWithSupportClassBuilder<?, ?> builder(@NonNull final SupportClass supportClass) {
    return new SuperBuilderWithSupportClassBuilderImpl(supportClass);
  }
}