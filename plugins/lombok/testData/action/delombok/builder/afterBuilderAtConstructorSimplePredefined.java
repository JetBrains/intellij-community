public class BuilderAtConstructorSimplePredefined {
  private int myInt;
  private String myString;

  public BuilderAtConstructorSimplePredefined(int myInt, String myString) {
    this.myInt = myInt;
    this.myString = myString;
  }

  public static BuilderAtConstructorSimplePredefinedBuilder builder() {
    return new BuilderAtConstructorSimplePredefinedBuilder();
  }

  static class BuilderAtConstructorSimplePredefinedBuilder {
    private int myInt;
    private String myString;

    BuilderAtConstructorSimplePredefinedBuilder() {
    }

    public BuilderAtConstructorSimplePredefinedBuilder myString(String myString) {
      this.myString = myString + "something";
      return this;
    }

    public BuilderAtConstructorSimplePredefinedBuilder myInt(int myInt) {
      this.myInt = myInt;
      return this;
    }

    public BuilderAtConstructorSimplePredefined build() {
      return new BuilderAtConstructorSimplePredefined(myInt, myString);
    }

    public String toString() {
      return "BuilderAtConstructorSimplePredefined.BuilderAtConstructorSimplePredefinedBuilder(myInt=" + this.myInt + ", myString=" + this.myString + ")";
    }
  }

  public static void main(String[] args) {
    BuilderAtConstructorSimplePredefined builderSimple = BuilderAtConstructorSimplePredefined.builder().myInt(123).myString("string").build();
    System.out.println(builderSimple);
  }
}
