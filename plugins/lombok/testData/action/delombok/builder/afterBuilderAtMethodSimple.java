public class BuilderAtMethodSimple {
  private int myInt;
  private String myString;

  public static BuilderAtMethodSimple createMe(int myInt, String myString) {
    BuilderAtMethodSimple result = new BuilderAtMethodSimple();
    result.myInt = myInt;
    result.myString = myString;
    return result;
  }

  public static void main(String[] args) {
    BuilderAtMethodSimple builderSimple = BuilderAtMethodSimple.builder().myInt(123).myString("string").build();
    System.out.println(builderSimple);
  }

  public static BuilderAtMethodSimpleBuilder builder() {
    return new BuilderAtMethodSimpleBuilder();
  }

  public static class BuilderAtMethodSimpleBuilder {
    private int myInt;
    private String myString;

    BuilderAtMethodSimpleBuilder() {
    }

    public BuilderAtMethodSimpleBuilder myInt(int myInt) {
      this.myInt = myInt;
      return this;
    }

    public BuilderAtMethodSimpleBuilder myString(String myString) {
      this.myString = myString;
      return this;
    }

    public BuilderAtMethodSimple build() {
      return BuilderAtMethodSimple.createMe(myInt, myString);
    }

    public String toString() {
      return "BuilderAtMethodSimple.BuilderAtMethodSimpleBuilder(myInt=" + this.myInt + ", myString=" + this.myString + ")";
    }
  }
}
