public class Example<T> {
  @lombok.Delegate
  private T delegator;

  @lombok.Delegate
  private int delegatorPrimitive;

  @lombok.Delegate
  private int[] delegatorPrimitiveArray;

  @lombok.Delegate
  private Integer[] delegatorArray;

  @lombok.Delegate
  private Integer delegatorInteger;
}