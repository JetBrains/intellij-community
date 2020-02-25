public class BuilderSimpleWithSetterPrefix {
  private int myInt;
  private String myString;

  BuilderSimpleWithSetterPrefix(int myInt, String myString) {
    this.myInt = myInt;
    this.myString = myString;
  }

  public static void main(String[] args) {
    BuilderSimpleWithSetterPrefix builderSimple = BuilderSimpleWithSetterPrefix.builder().setMyInt(123).setMyString("string").build();
    System.out.println(builderSimple);
  }

  public static BuilderSimpleWithSetterPrefixBuilder builder() {
    return new BuilderSimpleWithSetterPrefixBuilder();
  }

  public static class BuilderSimpleWithSetterPrefixBuilder {
    private int myInt;
    private String myString;

    BuilderSimpleWithSetterPrefixBuilder() {
    }

    public BuilderSimpleWithSetterPrefixBuilder setMyInt(int myInt) {
      this.myInt = myInt;
      return this;
    }

    public BuilderSimpleWithSetterPrefixBuilder setMyString(String myString) {
      this.myString = myString;
      return this;
    }

    public BuilderSimpleWithSetterPrefix build() {
      return new BuilderSimpleWithSetterPrefix(myInt, myString);
    }

    public String toString() {
      return "BuilderSimpleWithSetterPrefix.BuilderSimpleWithSetterPrefixBuilder(myInt=" + this.myInt + ", myString=" + this.myString + ")";
    }
  }
}
