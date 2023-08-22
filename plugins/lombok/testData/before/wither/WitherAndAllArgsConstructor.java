@lombok.AllArgsConstructor
class WitherAndAllArgsConstructor<T, J extends T, L extends java.lang.Number> {
	@lombok.experimental.Wither J test;
	
	@lombok.experimental.Wither java.util.List<L> test2;
	
	final int x = 10;
	
	int y = 20;
	
	final int z;
}