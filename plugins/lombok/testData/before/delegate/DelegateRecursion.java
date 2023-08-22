//skip compare content: This test is to see if the 'delegate recursion is not supported' error pops up.
import lombok.Delegate;
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
