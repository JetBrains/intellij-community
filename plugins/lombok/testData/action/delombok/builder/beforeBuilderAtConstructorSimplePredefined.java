public class BuilderAtConstructorSimplePredefined {
  private int myInt;
  private String myString;

  @lombok.Builder
  public BuilderAtConstructorSimplePredefined(int myInt, String myString) {
    this.myInt = myInt;
    this.myString = myString;
  }

  static class BuilderAtConstructorSimplePredefinedBuilder {
    private int myInt;

    public BuilderAtConstructorSimplePredefinedBuilder myString(String myString) {
      this.myString = myString + "something";
      return this;
    }
  }

  public static void main(String[] args) {
    BuilderAtConstructorSimplePredefined builderSimple = BuilderAtConstructorSimplePredefined.builder().myInt(123).myString("string").build();
    System.out.println(builderSimple);
  }
}
