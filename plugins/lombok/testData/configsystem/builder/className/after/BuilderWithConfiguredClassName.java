package before.builder.ClassName;

import lombok.Builder;

public class BuilderWithConfiguredClassName {

  private final String field;

  BuilderWithConfiguredClassName(final String field) {
    this.field = field;
  }

  public static final class Builder {

    @java.lang.SuppressWarnings("all")
    private String field;

    Builder() {
    }

    public Builder field(final String field) {
      this.field = field;
      return this;
    }

    public Builder copy() {
      return builder().field(field);
    }

    @java.lang.SuppressWarnings("all")
    public BuilderWithConfiguredClassName build() {
      return new BuilderWithConfiguredClassName(field);
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
      return "BuilderWithConfiguredClassName.Builder(field=" + this.field + ")";
    }

  }

  @java.lang.SuppressWarnings("all")
  public static Builder builder() {
    return new Builder();
  }
}
