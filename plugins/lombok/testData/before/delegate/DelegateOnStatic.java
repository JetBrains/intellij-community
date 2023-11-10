//platform !eclipse: Requires a 'full' eclipse with intialized workspace, and we don't (yet) have that set up properly in the test run.
//skip compare content

import lombok.experimental.Delegate;

class DelegateOnStatic {
	@Delegate private static final java.lang.Runnable staticField = null;
}

class DelegateOnStaticMethod {
	@Delegate private static final java.lang.Runnable staticMethod() {
		return null;
	};
}