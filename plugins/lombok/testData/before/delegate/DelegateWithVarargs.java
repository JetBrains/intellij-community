//platform !eclipse: Requires a 'full' eclipse with intialized workspace, and we don't (yet) have that set up properly in the test run.
import lombok.experimental.Delegate;

class DelegateWithVarargs {
	@Delegate private Bar bar;

	private interface Bar {
		void justOneParameter(int... varargs);
		void multipleParameters(String first, int... varargs);
		void array(int[] array);
		void arrayVarargs(int[]... arrayVarargs);
	}
}