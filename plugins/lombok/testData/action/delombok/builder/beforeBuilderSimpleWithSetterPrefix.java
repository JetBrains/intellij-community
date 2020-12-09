@lombok.Builder(setterPrefix = "set")
public class BuilderSimpleWithSetterPrefix {
  private int myInt;
  private String myString;

  public static void main(String[] args) {
    BuilderSimpleWithSetterPrefix builderSimple = BuilderSimpleWithSetterPrefix.builder().setMyInt(123).setMyString("string").build();
    System.out.println(builderSimple);
  }
}
