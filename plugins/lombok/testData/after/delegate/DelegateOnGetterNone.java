class DelegateOnGetterNone {
	private final Bar bar = null;
	private interface Bar {
		void setList(java.util.ArrayList<java.lang.String> list);
		int getInt();
	}
	@java.lang.SuppressWarnings("all")
	public void setList(final java.util.ArrayList<java.lang.String> list) {
		this.bar.setList(list);
	}
	@java.lang.SuppressWarnings("all")
	public int getInt() {
		return this.bar.getInt();
	}
}