class GetterOnMethod {
	int i;
	int j;
	int k;
	public @interface Test {
	}
	@Deprecated
	@java.lang.SuppressWarnings("all")
	public int getI() {
		return this.i;
	}
	@java.lang.Deprecated
	@Test
	@java.lang.SuppressWarnings("all")
	public int getJ() {
		return this.j;
	}
	@java.lang.Deprecated
	@Test
	@java.lang.SuppressWarnings("all")
	public int getK() {
		return this.k;
	}
}
