@lombok.Builder
public class BuilderWithDefaultRedundantInitializer {

  @lombok.Builder.Default
  private String field1 = " another value";

  private String filed2 = <warning descr="Variable 'filed2' initializer '\" some value\"' is redundant">" some value"</warning>;

  public static void main(String[] args) {
    System.out.println(BuilderWithDefaultRedundantInitializer.builder().field1("xyz").build());
  }
}
