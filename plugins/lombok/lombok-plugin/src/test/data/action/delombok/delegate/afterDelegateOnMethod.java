public class DelegateOnMethod implements java.util.concurrent.Callable<Void> {
  private java.util.concurrent.Callable<Void> calcDelegated() {
    return new java.util.concurrent.Callable<Void>() {
      @Override
      public Void call() throws Exception {
        return null;
      }
    };
  }

  public Void call() throws Exception {
    return this.calcDelegated().call();
  }
}