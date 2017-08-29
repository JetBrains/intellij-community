@lombok.Builder
public class BuilderSimplePreDefined {
  private int myInt;
  private String myString;

  static class BuilderSimplePreDefinedBuilder {
    private int myInt;

    public BuilderSimplePreDefinedBuilder myString(String myString) {
      this.myString = myString + "something";
      return this;
    }
  }

  public static void main(String[] args) {
    BuilderSimplePreDefined builderSimple = BuilderSimplePreDefined.builder().myInt(123).myString("string").build();
    System.out.println(builderSimple);
  }
}
