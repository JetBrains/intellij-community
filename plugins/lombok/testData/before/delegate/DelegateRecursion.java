//platform !eclipse: Requires a 'full' eclipse with intialized workspace, and we don't (yet) have that set up properly in the test run.
//skip compare content: This test is to see if the 'delegate recursion is not supported' error pops up.
import lombok.experimental.Delegate;
class DelegateRecursionOuterMost {
	@Delegate
	private final DelegateRecursionCenter center = new DelegateRecursionCenter();
}

class DelegateRecursionCenter {
	@Delegate
	private final DelegateRecursionInnerMost inner = new DelegateRecursionInnerMost();
}

class DelegateRecursionInnerMost {
	public void innerMostMethod() {
	}
}
