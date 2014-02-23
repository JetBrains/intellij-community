public class Example<T> {
  @lombok.Delegate
  private static Integer calcDelegatorInteger() {
    return 1;
  }

  @lombok.Delegate
  private static Float someFloat;

}