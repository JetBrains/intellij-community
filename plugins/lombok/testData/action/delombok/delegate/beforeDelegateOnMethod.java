public class DelegateOnMethod implements Callable<Void> {
  @lombok.Delegate
  private Callable<Void> calcDelegated() {
    return new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        return null;
      }
    };
  }
}
interface Callable<V> {
  V call() throws Exception;
}
class Exception {

}