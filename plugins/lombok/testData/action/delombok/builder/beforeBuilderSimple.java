@lombok.Builder
public class BuilderSimple {
  private int myInt;
  private String myString;

  public static void main(String[] args) {
    BuilderSimple builderSimple = BuilderSimple.builder().myInt(123).myString("string").build();
    System.out.println(builderSimple);
  }
}
