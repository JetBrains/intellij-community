class DelegateWithVarargs {
	private Bar bar;
	private interface Bar {
		void justOneParameter(int... varargs);
		void multipleParameters(String first, int... varargs);
		void array(int[] array);
		void arrayVarargs(int[]... arrayVarargs);
	}
	@java.lang.SuppressWarnings("all")
	public void justOneParameter(final int... varargs) {
		this.bar.justOneParameter(varargs);
	}
	@java.lang.SuppressWarnings("all")
	public void multipleParameters(final java.lang.String first, final int... varargs) {
		this.bar.multipleParameters(first, varargs);
	}
	@java.lang.SuppressWarnings("all")
	public void array(final int[] array) {
		this.bar.array(array);
	}
	@java.lang.SuppressWarnings("all")
	public void arrayVarargs(final int[]... arrayVarargs) {
		this.bar.arrayVarargs(arrayVarargs);
	}
}
