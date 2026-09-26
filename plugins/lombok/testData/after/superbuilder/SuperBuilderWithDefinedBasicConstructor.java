public class SuperBuilderWithDefinedBasicConstructor<T> {

  public SuperBuilderWithDefinedBasicConstructor(T something) {
  }

  protected SuperBuilderWithDefinedBasicConstructor(SuperBuilderWithDefinedBasicConstructorBuilder<T, ?, ?> b) {
  }

  public static <T> SuperBuilderWithDefinedBasicConstructorBuilder<T, ?, ?> builder() {
    return new SuperBuilderWithDefinedBasicConstructor.SuperBuilderWithDefinedBasicConstructorBuilderImpl<T>();
  }

  public static abstract class SuperBuilderWithDefinedBasicConstructorBuilder<T, C extends SuperBuilderWithDefinedBasicConstructor<T>, B extends SuperBuilderWithDefinedBasicConstructorBuilder<T, C, B>> {
    protected abstract B self();

    public abstract C build();

    public String toString() {
      return "SuperBuilderWithDefinedBasicConstructor.SuperBuilderWithDefinedBasicConstructorBuilder()";
    }
  }

  private static final class SuperBuilderWithDefinedBasicConstructorBuilderImpl<T> extends SuperBuilderWithDefinedBasicConstructorBuilder<T, SuperBuilderWithDefinedBasicConstructor<T>, SuperBuilderWithDefinedBasicConstructorBuilderImpl<T>> {
    private SuperBuilderWithDefinedBasicConstructorBuilderImpl() {
    }

    protected SuperBuilderWithDefinedBasicConstructorBuilderImpl<T> self() {
      return this;
    }

    public SuperBuilderWithDefinedBasicConstructor<T> build() {
      return new SuperBuilderWithDefinedBasicConstructor<T>(this);
    }
  }
}