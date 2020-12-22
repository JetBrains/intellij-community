class DelegateOnGetter {
	private final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<Bar>> bar = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<Bar>>();
	private interface Bar {
		void setList(java.util.ArrayList<java.lang.String> list);
		int getInt();
	}
	@java.lang.SuppressWarnings("all")
	public Bar getBar() {
		java.util.concurrent.atomic.AtomicReference<Bar> value = this.bar.get();
		if (value == null) {
			synchronized (this.bar) {
				value = this.bar.get();
				if (value == null) {
					final Bar actualValue = new Bar(){
						public void setList(java.util.ArrayList<String> list) {
						}
						public int getInt() {
							return 42;
						}
					};
					value = new java.util.concurrent.atomic.AtomicReference<Bar>(actualValue);
					this.bar.set(value);
				}
			}
		}
		return value.get();
	}
	@java.lang.SuppressWarnings("all")
	public void setList(final java.util.ArrayList<java.lang.String> list) {
		this.getBar().setList(list);
	}
	@java.lang.SuppressWarnings("all")
	public int getInt() {
		return this.getBar().getInt();
	}
}