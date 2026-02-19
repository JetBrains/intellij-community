import lombok.experimental.Delegate;

class DelegateWithVarargs2 {
  @Delegate private DelegateWithVarargs2.B bar;

  public class B {
    public void varargs(Object[]... keys) { }
  }
}
