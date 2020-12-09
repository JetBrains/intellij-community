@lombok.Builder
public class Issue633 {

  @lombok.Builder.Default
  private String field1 = " another value";

  private String filed2 = <warning descr="Variable 'filed2' initializer '\" some value\"' is redundant">" some value"</warning>;
}
