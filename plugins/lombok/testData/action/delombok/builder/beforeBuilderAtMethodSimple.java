public class BuilderAtMethodSimple {
  private int myInt;
  private String myString;

  @lombok.Builder
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
}