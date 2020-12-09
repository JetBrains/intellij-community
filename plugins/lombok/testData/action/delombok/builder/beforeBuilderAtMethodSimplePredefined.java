public class BuilderAtMethodSimplePredefined {
  private int myInt;
  private String myString;

  @lombok.Builder
  public static BuilderAtMethodSimplePredefined createMe(int myInt, String myString) {
    BuilderAtMethodSimplePredefined result = new BuilderAtMethodSimplePredefined();
    result.myInt = myInt;
    result.myString = myString;
    return result;
  }

  static class BuilderAtMethodSimplePredefinedBuilder {
    private int myInt;

    public BuilderAtMethodSimplePredefinedBuilder myString(String myString) {
      this.myString = myString + "something";
      return this;
    }
  }

  public static void main(String[] args) {
    BuilderAtMethodSimplePredefined builderSimple = BuilderAtMethodSimplePredefined.builder().myInt(123).myString("string").build();
    System.out.println(builderSimple);
  }
}
