import lombok.Builder;

@Builder
public class DefaultBuilderFinalValueInspectionIsAlwaysThat {
  @Builder.Default final boolean b = true;
  final boolean b2 = true;

  public void test() {
    while(b) {
      System.out.println("hello");
    }
    System.out.println();
  }

  public void test2() {
    while(b2) {
      System.out.println("hello");
    }
    <error descr="Unreachable statement">System.out.println();</error>
  }
}
