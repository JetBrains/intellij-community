@lombok.AllArgsConstructor
class WithAndAllArgsConstructor<T, J extends T, L extends Number> {
	@lombok.With J test;

	@lombok.With java.util.List<L> test2;

	final int x = 10;

	int y = 20;

	final int z;
}
