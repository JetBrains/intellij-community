public class BuilderAtMethodSimplePredefined {
  private int myInt;
  private String myString;

  public static BuilderAtMethodSimplePredefined createMe(int myInt, String myString) {
    BuilderAtMethodSimplePredefined result = new BuilderAtMethodSimplePredefined();
    result.myInt = myInt;
    result.myString = myString;
    return result;
  }

  public static BuilderAtMethodSimplePredefinedBuilder builder() {
    return new BuilderAtMethodSimplePredefinedBuilder();
  }

  static class BuilderAtMethodSimplePredefinedBuilder {
    private int myInt;
    private String myString;

    BuilderAtMethodSimplePredefinedBuilder() {
    }

    public BuilderAtMethodSimplePredefinedBuilder myString(String myString) {
      this.myString = myString + "something";
      return this;
    }

    public BuilderAtMethodSimplePredefinedBuilder myInt(int myInt) {
      this.myInt = myInt;
      return this;
    }

    public BuilderAtMethodSimplePredefined build() {
      return BuilderAtMethodSimplePredefined.createMe(myInt, myString);
    }

    public String toString() {
      return "BuilderAtMethodSimplePredefined.BuilderAtMethodSimplePredefinedBuilder(myInt=" + this.myInt + ", myString=" + this.myString + ")";
    }
  }

  public static void main(String[] args) {
    BuilderAtMethodSimplePredefined builderSimple = BuilderAtMethodSimplePredefined.builder().myInt(123).myString("string").build();
    System.out.println(builderSimple);
  }
}
