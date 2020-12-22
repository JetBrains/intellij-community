import lombok.Builder;


@Builder
public class PointlessBooleanExpressionBuilderDefault {
  @Builder.Default
  final boolean b = true;
  final boolean bIsTrueFinal = true;

  public void test() {
    // should be here
    if (<warning descr="'bIsTrueFinal == true' can be simplified to 'bIsTrueFinal'">bIsTrueFinal == true</warning>) {
      System.out.println("ConstantConditions");
    }

    // shouldn't be here because the b is not 100% initialized with 'true' value
    if (b == true) {
      System.out.println("ConstantConditions");
    }
  }
}
