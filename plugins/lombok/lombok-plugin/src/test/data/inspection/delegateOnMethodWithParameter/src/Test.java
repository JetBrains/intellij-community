public class Example<T> {
  @lombok.Delegate
  private Integer calcDelegatorInteger() {
    return 1;
  }

  @lombok.Delegate
  private Integer calcDelegatorWithParam(int param1) {
    return 1;
  }

  @lombok.Delegate
  private Integer calcDelegatorWithParams(int param1, String param2) {
    return 1;
  }
}