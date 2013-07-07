class DelegateWithDeprecated {
	private Bar bar;
	private interface Bar {
		@Deprecated
		void deprecatedAnnotation();
		/** @deprecated */
		void deprecatedComment();
		void notDeprecated();
	}
	@java.lang.Deprecated
	@java.lang.SuppressWarnings("all")
	public void deprecatedAnnotation() {
		this.bar.deprecatedAnnotation();
	}
	@java.lang.Deprecated
	@java.lang.SuppressWarnings("all")
	public void deprecatedComment() {
		this.bar.deprecatedComment();
	}
	@java.lang.SuppressWarnings("all")
	public void notDeprecated() {
		this.bar.notDeprecated();
	}
}