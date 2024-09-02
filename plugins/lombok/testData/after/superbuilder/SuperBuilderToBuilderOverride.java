
class Base {
  private String string;

  protected Base(BaseBuilder<?, ?> b) {
    this.string = b.string;
  }

  public static BaseBuilder<?, ?> builder() {
    return new Base.BaseBuilderImpl();
  }

  public String getString() {
    return this.string;
  }

  public BaseBuilder<?, ?> toBuilder() {
    return new Base.BaseBuilderImpl().$fillValuesFrom(this);
  }

  public static abstract class BaseBuilder<C extends Base, B extends BaseBuilder<C, B>> {
    private String string;

    private static void $fillValuesFromInstanceIntoBuilder(Base instance, BaseBuilder<?, ?> b) {
      b.string(instance.string);
    }

    public B string(String string) {
      this.string = string;
      return self();
    }

    protected B $fillValuesFrom(C instance) {
      Base.BaseBuilder.$fillValuesFromInstanceIntoBuilder(instance, this);
      return self();
    }

    protected abstract B self();

    public abstract C build();

    public String toString() {
      return "Base.BaseBuilder(string=" + this.string + ")";
    }
  }

  private static final class BaseBuilderImpl extends BaseBuilder<Base, BaseBuilderImpl> {
    private BaseBuilderImpl() {
    }

    protected BaseBuilderImpl self() {
      return this;
    }

    public Base build() {
      return new Base(this);
    }
  }
}


class Sub extends Base {
  private int number;

  protected Sub(SubBuilder<?, ?> builder) {
    super(builder);
  }

  public static SubBuilder<?, ?> builder() {
    return new Sub.SubBuilderImpl();
  }

  public SubBuilder<?, ?> toBuilder() {
    // custom code should go here
    return new SubBuilderImpl().$fillValuesFrom(this);
  }

  public int getNumber() {
    return this.number;
  }

  public static abstract class SubBuilder<C extends Sub, B extends SubBuilder<C, B>> extends BaseBuilder<C, B> {
    private int number;

    private static void $fillValuesFromInstanceIntoBuilder(Sub instance, SubBuilder<?, ?> b) {
      b.number(instance.number);
    }

    public B number(int number) {
      this.number = number;
      return self();
    }

    protected B $fillValuesFrom(C instance) {
      super.$fillValuesFrom(instance);
      Sub.SubBuilder.$fillValuesFromInstanceIntoBuilder(instance, this);
      return self();
    }

    protected abstract B self();

    public abstract C build();

    public String toString() {
      return "Sub.SubBuilder(super=" + super.toString() + ", number=" + this.number + ")";
    }
  }

  private static final class SubBuilderImpl extends SubBuilder<Sub, SubBuilderImpl> {
    private SubBuilderImpl() {
    }

    protected SubBuilderImpl self() {
      return this;
    }

    public Sub build() {
      return new Sub(this);
    }
  }
}
