public class ConcreteType<T> {
  <error descr="@Delegate can only use concrete class types, not wildcards, arrays, type variables, or primitives. 'T' is wrong class type">@lombok.Delegate</error>
  private T delegator;

  <error descr="@Delegate can only use concrete class types, not wildcards, arrays, type variables, or primitives. 'int' is wrong class type">@lombok.Delegate</error>
  private int delegatorPrimitive;

  <error descr="@Delegate can only use concrete class types, not wildcards, arrays, type variables, or primitives. 'int[]' is wrong class type">@lombok.Delegate</error>
  private int[] delegatorPrimitiveArray;

  <error descr="@Delegate can only use concrete class types, not wildcards, arrays, type variables, or primitives. 'java.lang.Integer[]' is wrong class type">@lombok.Delegate</error>
  private Integer[] delegatorArray;

  @lombok.Delegate
  private Integer delegatorInteger;
}
