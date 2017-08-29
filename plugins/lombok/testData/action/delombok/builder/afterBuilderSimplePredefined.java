public class BuilderSimplePreDefined {
  private int myInt;
  private String myString;

  @java.beans.ConstructorProperties({"myInt", "myString"})
  BuilderSimplePreDefined(int myInt, String myString) {
    this.myInt = myInt;
    this.myString = myString;
  }

  public static BuilderSimplePreDefinedBuilder builder() {
    return new BuilderSimplePreDefinedBuilder();
  }

  static class BuilderSimplePreDefinedBuilder {
    private int myInt;
    private String myString;

    BuilderSimplePreDefinedBuilder() {
    }

    public BuilderSimplePreDefinedBuilder myString(String myString) {
      this.myString = myString + "something";
      return this;
    }

    public BuilderSimplePreDefinedBuilder myInt(int myInt) {
      this.myInt = myInt;
      return this;
    }

    public BuilderSimplePreDefined build() {
      return new BuilderSimplePreDefined(myInt, myString);
    }

    public String toString() {
      return "BuilderSimplePreDefined.BuilderSimplePreDefinedBuilder(myInt=" + this.myInt + ", myString=" + this.myString + ")";
    }
  }

  public static void main(String[] args) {
    BuilderSimplePreDefined builderSimple = BuilderSimplePreDefined.builder().myInt(123).myString("string").build();
    System.out.println(builderSimple);
  }
}
