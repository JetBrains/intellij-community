
public class BuilderWithDefaultInnerFields {
  private Object bar = new Object();

  private static Object $default$bar() {
    return new Object();
  }

  BuilderWithDefaultInnerFields(Object bar) {
    this.bar = bar;
  }

  public static BuilderWithDefaultInnerFields.BuilderWithDefaultInnerFieldsBuilder builder() {
    return new BuilderWithDefaultInnerFieldsBuilder();
  }

  public BuilderWithDefaultInnerFields.BuilderWithDefaultInnerFieldsBuilder toBuilder() {
    return new BuilderWithDefaultInnerFieldsBuilder().bar(this.bar);
  }

  public static class BuilderWithDefaultInnerFieldsBuilder {

    private boolean bar$set;
    private Object bar$value;

    public BuilderWithDefaultInnerFields.BuilderWithDefaultInnerFieldsBuilder barManual(Object bar) {
      this.bar$value = bar;
      this.bar$set = true;
      return this;
    }

    BuilderWithDefaultInnerFieldsBuilder() {
    }

    public BuilderWithDefaultInnerFields.BuilderWithDefaultInnerFieldsBuilder bar(Object bar) {
      this.bar$value = bar;
      this.bar$set = true;
      return this;
    }

    public BuilderWithDefaultInnerFields build() {
      Object bar$value = this.bar$value;
      if (!this.bar$set) {
        bar$value = BuilderWithDefaultInnerFields.$default$bar();
      }

      return new BuilderWithDefaultInnerFields(bar$value);
    }

    public String toString() {
      return "BuilderWithDefaultInnerFields.BuilderWithDefaultInnerFieldsBuilder(bar$value=" + this.bar$value + ")";
    }
  }
}
