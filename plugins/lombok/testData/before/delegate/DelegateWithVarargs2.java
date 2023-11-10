//platform !eclipse: Requires a 'full' eclipse with intialized workspace, and we don't (yet) have that set up properly in the test run.
import lombok.experimental.Delegate;

class DelegateWithVarargs2 {
	@Delegate private DelegateWithVarargs2.B bar;

	public class B {
		public void varargs(Object[]... keys) {}
	}
}
