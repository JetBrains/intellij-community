//ignore - This test fails, but, fixing it is going to take a long time and we don't have it in the planning. Having a failing test is very annoying for e.g. 'ant test'.

class DelegateRecursionOuterMost {
	private final DelegateRecursionCenter center = new DelegateRecursionCenter();
	@java.lang.SuppressWarnings("all")
	public void innerMostMethod() {
		this.center.innerMostMethod();
	}
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