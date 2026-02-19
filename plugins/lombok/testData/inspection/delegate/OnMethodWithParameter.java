public class OnMethodWithParameter<T> {
  @lombok.Delegate
  private Integer calcDelegatorInteger() {
    return 1;
  }

  <error descr="@Delegate is legal only on no-argument methods.">@lombok.Delegate</error>
  private Integer calcDelegatorWithParam(int param1) {
    return 1;
  }

  <error descr="@Delegate is legal only on no-argument methods.">@lombok.Delegate</error>
  private Integer calcDelegatorWithParams(int param1, String param2) {
    return 1;
  }
}
