class WithWithGenerics<T, J extends T, L extends Number> {
	@lombok.With J test;
	@lombok.With java.util.List<L> test2;
	@lombok.With java.util.List<? extends L> test3;
	int $i;

	public WithWithGenerics(J test, java.util.List<L> test2, java.util.List<? extends L> test3) {
	}
}
