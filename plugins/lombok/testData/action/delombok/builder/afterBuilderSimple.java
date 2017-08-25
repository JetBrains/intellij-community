public class BuilderSimple {
  private int myInt;
  private String myString;

  @java.beans.ConstructorProperties({"myInt", "myString"})
  BuilderSimple(int myInt, String myString) {
    this.myInt = myInt;
    this.myString = myString;
  }

  public static void main(String[] args) {
    BuilderSimple builderSimple = BuilderSimple.builder().myInt(123).myString("string").build();
    System.out.println(builderSimple);
  }

  public static BuilderSimpleBuilder builder() {
    return new BuilderSimpleBuilder();
  }

  public static class BuilderSimpleBuilder {
    private int myInt;
    private String myString;

    BuilderSimpleBuilder() {
    }

    public BuilderSimpleBuilder myInt(int myInt) {
      this.myInt = myInt;
      return this;
    }

    public BuilderSimpleBuilder myString(String myString) {
      this.myString = myString;
      return this;
    }

    public BuilderSimple build() {
      return new BuilderSimple(myInt, myString);
    }

    public String toString() {
      return "BuilderSimple.BuilderSimpleBuilder(myInt=" + this.myInt + ", myString=" + this.myString + ")";
    }
  }
}
