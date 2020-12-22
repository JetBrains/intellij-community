//skip compare content: This test is to see if the 'delegate recursion is not supported' error pops up.
class DelegateRecursionOuterMost {
	private final DelegateRecursionCenter center = new DelegateRecursionCenter();
}
class DelegateRecursionCenter {
	private final DelegateRecursionInnerMost inner = new DelegateRecursionInnerMost();
	@java.lang.SuppressWarnings("all")
	public void innerMostMethod() {
		this.inner.innerMostMethod();
	}
}
class DelegateRecursionInnerMost {
	public void innerMostMethod() {
	}
}
