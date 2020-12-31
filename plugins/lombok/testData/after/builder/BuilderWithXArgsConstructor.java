import lombok.Generated;

public class BuilderWithXArgsConstructor {
    private int someProperty;

    @Generated
    public static BuilderWithXArgsConstructor.BuilderWithXArgsConstructorBuilder builder() {
        return new BuilderWithXArgsConstructorBuilder();
    }

    @Generated
    public BuilderWithXArgsConstructor(int someProperty) {
        this.someProperty = someProperty;
    }

    @Generated
    public static class BuilderWithXArgsConstructorBuilder {
        @Generated
        private int someProperty;

        @Generated
        BuilderWithXArgsConstructorBuilder() {
        }

        @Generated
        public BuilderWithXArgsConstructor.BuilderWithXArgsConstructorBuilder someProperty(int someProperty) {
            this.someProperty = someProperty;
            return this;
        }

        @Generated
        public BuilderWithXArgsConstructor build() {
            return new BuilderWithXArgsConstructor(someProperty);
        }

        @Generated
        public String toString() {
            return "BuilderWithXArgsConstructor.BuilderWithXArgsConstructorBuilder(someProperty=" + this.someProperty + ")";
        }
    }
}
class BuilderWithAllArgsConstructorPrivate {
  private int someProperty;

  @Generated
  public static BuilderWithAllArgsConstructorPrivate.BuilderWithAllArgsConstructorPrivateBuilder builder() {
    return new BuilderWithAllArgsConstructorPrivateBuilder();
  }

  @Generated
  private BuilderWithAllArgsConstructorPrivate(int someProperty) {
    this.someProperty = someProperty;
  }

  @Generated
  public static class BuilderWithAllArgsConstructorPrivateBuilder {
    @Generated
    private int someProperty;

    @Generated
    BuilderWithAllArgsConstructorPrivateBuilder() {
    }

    @Generated
    public BuilderWithAllArgsConstructorPrivate.BuilderWithAllArgsConstructorPrivateBuilder someProperty(int someProperty) {
      this.someProperty = someProperty;
      return this;
    }

    @Generated
    public BuilderWithAllArgsConstructorPrivate build() {
      return new BuilderWithAllArgsConstructorPrivate(someProperty);
    }

    @Generated
    public String toString() {
      return "BuilderWithAllArgsConstructorPrivate.BuilderWithAllArgsConstructorPrivateBuilder(someProperty=" + this.someProperty + ")";
    }
  }
}
class BuilderWithReqArgsConstructor {
  private final int someProperty;

  @Generated
  public static BuilderWithReqArgsConstructor.BuilderWithReqArgsConstructorBuilder builder() {
    return new BuilderWithReqArgsConstructorBuilder();
  }

  @Generated
  public BuilderWithReqArgsConstructor(int someProperty) {
    this.someProperty = someProperty;
  }

  @Generated
  public static class BuilderWithReqArgsConstructorBuilder {
    @Generated
    private int someProperty;

    @Generated
    BuilderWithReqArgsConstructorBuilder() {
    }

    @Generated
    public BuilderWithReqArgsConstructor.BuilderWithReqArgsConstructorBuilder someProperty(int someProperty) {
      this.someProperty = someProperty;
      return this;
    }

    @Generated
    public BuilderWithReqArgsConstructor build() {
      return new BuilderWithReqArgsConstructor(someProperty);
    }

    @Generated
    public String toString() {
      return "BuilderWithReqArgsConstructor.BuilderWithReqArgsConstructorBuilder(someProperty=" + this.someProperty + ")";
    }
  }
}
class BuilderWithReqArgsConstructorPrivate {
  private final int someProperty;

  @Generated
  public static BuilderWithReqArgsConstructorPrivate.BuilderWithReqArgsConstructorPrivateBuilder builder() {
    return new BuilderWithReqArgsConstructorPrivateBuilder();
  }

  @Generated
  private BuilderWithReqArgsConstructorPrivate(int someProperty) {
    this.someProperty = someProperty;
  }

  @Generated
  public static class BuilderWithReqArgsConstructorPrivateBuilder {
    @Generated
    private int someProperty;

    @Generated
    BuilderWithReqArgsConstructorPrivateBuilder() {
    }

    @Generated
    public BuilderWithReqArgsConstructorPrivate.BuilderWithReqArgsConstructorPrivateBuilder someProperty(int someProperty) {
      this.someProperty = someProperty;
      return this;
    }

    @Generated
    public BuilderWithReqArgsConstructorPrivate build() {
      return new BuilderWithReqArgsConstructorPrivate(someProperty);
    }

    @Generated
    public String toString() {
      return "BuilderWithReqArgsConstructorPrivate.BuilderWithReqArgsConstructorPrivateBuilder(someProperty=" + this.someProperty + ")";
    }
  }
}
