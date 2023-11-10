//platform !eclipse: Requires a 'full' eclipse with intialized workspace, and we don't (yet) have that set up properly in the test run.
import lombok.experimental.Delegate;
class DelegatePlain {
	@Delegate(types = Bar.class)
	private final BarImpl bar = new BarImpl();
	@Delegate(types = Foo.class, excludes = Bar.class)
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
}