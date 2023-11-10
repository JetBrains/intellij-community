//platform !eclipse: Requires a 'full' eclipse with intialized workspace, and we don't (yet) have that set up properly in the test run.
import lombok.Delegate;
import lombok.Getter;

class DelegateOnGetter {

	@Delegate @Getter(lazy=true) private final Bar bar = new Bar() { 
		public void setList(java.util.ArrayList<String> list) {
		}
		public int getInt() {
			return 42;
		}
	};

	private interface Bar {
		void setList(java.util.ArrayList<java.lang.String> list);
		int getInt();
	}
}