class DelegateWithVarargs2 {
	private DelegateWithVarargs2.B bar;
	public class B {
		public void varargs(Object[]... keys) {
		}
	}
	@java.lang.SuppressWarnings("all")
	public void varargs(final java.lang.Object[]... keys) {
		this.bar.varargs(keys);
	}
}

