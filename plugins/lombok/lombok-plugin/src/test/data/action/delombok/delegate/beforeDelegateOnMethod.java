public class DelegateOnMethod implements java.util.concurrent.Callable<Void> {
  @lombok.Delegate
  private java.util.concurrent.Callable<Void> calcDelegated() {
    return new java.util.concurrent.Callable<Void>() {
      @Override
      public Void call() throws Exception {
        return null;
      }
    };
  }
}