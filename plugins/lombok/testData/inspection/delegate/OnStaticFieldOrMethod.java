public class OnStaticFieldOrMethod<T> {
  <error descr="@Delegate is legal only on instance fields or no-argument instance methods.">@lombok.Delegate</error>
  private static Integer calcDelegatorInteger() {
    return 1;
  }

  <error descr="@Delegate is legal only on instance fields or no-argument instance methods.">@lombok.Delegate</error>
  private static Float someFloat;

}
