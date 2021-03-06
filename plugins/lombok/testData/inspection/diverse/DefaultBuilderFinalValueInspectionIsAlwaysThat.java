import lombok.Builder;

@Builder
public class DefaultBuilderFinalValueInspectionIsAlwaysThat {
  @Builder.Default final boolean b = true;
  @Builder.Default final int i = -1;
  @Builder.Default final String s = "";
  @Builder.Default final Object o = new Object();
  final String thisIsTrueFinalField = "";

  public void test() {
    // Doesn't occur here
    if (b) System.out.println("no warning");

    // But does occur here and with other primitives
    if (b ==  true) System.out.println("ConstantConditions");
    if (i == 0) System.out.println("ConstantConditions");

    // Does occur on objects
    if (s.isEmpty()) System.out.println("ConstantConditions");
    if (o == null) System.out.println("ConstantConditions");

    // Must occur here
    if (<warning descr="Condition 'thisIsTrueFinalField.isEmpty()' is always 'true'">thisIsTrueFinalField.isEmpty()</warning>) System.out.println("ConstantConditions");
  }
}
