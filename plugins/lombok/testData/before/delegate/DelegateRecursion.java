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