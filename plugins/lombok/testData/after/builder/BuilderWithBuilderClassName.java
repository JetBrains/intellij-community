public final class BuilderWithBuilderClassName {
  private final String name;
  private final int age;

  BuilderWithBuilderClassName(String name, int age) {
    this.name = name;
    this.age = age;
  }

  public static BuilderWithBuilderClassName.Builder builder() {
    return new Builder();
  }

  public String getName() {
    return this.name;
  }

  public int getAge() {
    return this.age;
  }

  public boolean equals(Object o) {
    if (o == this) returntrue;
    if (!(o instanceof BuilderWithBuilderClassName)) return false;
    final BuilderWithBuilderClassName other = (BuilderWithBuilderClassName)o;
    final Object this$name = this.getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
    if (this.getAge() != other.getAge()) return false;
    return true;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $name = this.getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    result = result * PRIME + this.getAge();
    return result;
  }

  public String toString() {
    return "BuilderWithBuilderClassName(name=" + this.getName() + ", age=" + this.getAge() + ")";
  }

  public static class Builder {
    private String name;
    private int age;

    Builder() {
    }

    public BuilderWithBuilderClassName.Builder name(String name) {
      this.name = name;
      return this;
    }

    public BuilderWithBuilderClassName.Builder age(int age) {
      this.age = age;
      return this;
    }

    public BuilderWithBuilderClassName build() {
      return new BuilderWithBuilderClassName(this.name, this.age);
    }

    public String toString() {
      return "BuilderWithBuilderClassName.Builder(name=" + this.name + ", age=" + this.age + ")";
    }
  }
}
