class WithWithGenerics<T, J extends T, L extends Number> {
	J test;
	java.util.List<L> test2;
	java.util.List<? extends L> test3;
	int $i;
	public WithWithGenerics(J test, java.util.List<L> test2, java.util.List<? extends L> test3) {
	}
	@SuppressWarnings("all")
	public WithWithGenerics<T, J, L> withTest(final J test) {
		return this.test == test ? this : new WithWithGenerics<T, J, L>(test, this.test2, this.test3);
	}
	@SuppressWarnings("all")
	public WithWithGenerics<T, J, L> withTest2(final java.util.List<L> test2) {
		return this.test2 == test2 ? this : new WithWithGenerics<T, J, L>(this.test, test2, this.test3);
	}
	@SuppressWarnings("all")
	public WithWithGenerics<T, J, L> withTest3(final java.util.List<? extends L> test3) {
		return this.test3 == test3 ? this : new WithWithGenerics<T, J, L>(this.test, this.test2, test3);
	}
}
