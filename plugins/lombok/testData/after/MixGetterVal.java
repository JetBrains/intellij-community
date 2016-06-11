class MixGetterVal {
	private int x;
	public void m(int z) {
	}

	public void test() {
		final int y = x;
		m(y);
	}

	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public int getX() {
		return this.x;
	}
}
