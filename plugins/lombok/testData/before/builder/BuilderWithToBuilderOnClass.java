@lombok.Builder(toBuilder = true)
class BuilderWithToBuilderOnClass<T> {
	private String one, two;

	@lombok.Builder.ObtainVia(method = "rrr", isStatic = true)
	private T foo;

	private int bar;

	public static <K> K rrr(BuilderWithToBuilderOnClass<K> x) {
		return x.foo;
	}
}