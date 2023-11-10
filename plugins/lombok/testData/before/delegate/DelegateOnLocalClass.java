//platform !eclipse: Requires a 'full' eclipse with intialized workspace, and we don't (yet) have that set up properly in the test run.
//skip compare content
//ignore: crashed javac with NPE, should be enabled when that bug is fixed

import lombok.experimental.Delegate;

interface DelegateOnLocalClass {
	void test1() {
		class DelegateOnStatic {
			@Delegate private final java.lang.Runnable field = null;
		}
	}

	void test2() {
		Runnable r = new Runnable() {
			@Delegate private final java.lang.Runnable field = null;
		}
	}
}
