public class DelegateWithException implements java.util.concurrent.Callable<Integer> {

  private final java.util.concurrent.Callable<Integer> delegated;

  public DelegateWithException(java.util.concurrent.Callable<Integer> delegated) {
    this.delegated = delegated;
  }

  public static void main(String[] args) throws Exception {
    DelegateWithException myCallable = new DelegateWithException(new java.util.concurrent.Callable<Integer>() {
      @Override
      public Integer call() throws Exception {
        return 1;
      }
    });

    System.out.println(myCallable.call());
  }

  public Integer call() throws Exception {
    return this.delegated.call();
  }
}
