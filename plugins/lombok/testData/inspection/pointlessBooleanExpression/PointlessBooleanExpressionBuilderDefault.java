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

    if (<warning descr="'b == true' can be simplified to 'b'">b == true</warning>) {
      System.out.println("ConstantConditions");
    }
  }
}
