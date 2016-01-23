class DelegatePlain {
	private final BarImpl bar = new BarImpl();
	private final FooImpl foo = new FooImpl();

	private static class FooImpl implements Foo {
		public void foo() {
		}

		public void bar(java.util.ArrayList<java.lang.String> list) {
		}
	}

	private static class BarImpl implements Bar {
		public void bar(java.util.ArrayList<java.lang.String> list) {
		}
	}

	private static interface Foo extends Bar {
		void foo();
	}

	private static interface Bar {
		void bar(java.util.ArrayList<java.lang.String> list);
	}

	@java.lang.SuppressWarnings("all")
	public void bar(final java.util.ArrayList<java.lang.String> list) {
		this.bar.bar(list);
	}

	@java.lang.SuppressWarnings("all")
	public void foo() {
		this.foo.foo();
	}
}