class WitherAndAllArgsConstructor<T, J extends T, L extends java.lang.Number> {
	J test;
	java.util.List<L> test2;
	final int x = 10;
	int y = 20;
	final int z;
	@java.beans.ConstructorProperties({"test", "test2", "y", "z"})
	@java.lang.SuppressWarnings("all")
	public WitherAndAllArgsConstructor(final J test, final java.util.List<L> test2, final int y, final int z) {
		this.test = test;
		this.test2 = test2;
		this.y = y;
		this.z = z;
	}
	@java.lang.SuppressWarnings("all")
	public WitherAndAllArgsConstructor<T, J, L> withTest(final J test) {
		return this.test == test ? this : new WitherAndAllArgsConstructor<T, J, L>(test, this.test2, this.y, this.z);
	}
	@java.lang.SuppressWarnings("all")
	public WitherAndAllArgsConstructor<T, J, L> withTest2(final java.util.List<L> test2) {
		return this.test2 == test2 ? this : new WitherAndAllArgsConstructor<T, J, L>(this.test, test2, this.y, this.z);
	}
}