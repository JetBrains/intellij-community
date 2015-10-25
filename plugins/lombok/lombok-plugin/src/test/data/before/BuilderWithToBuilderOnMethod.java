class BuilderWithToBuilderOnMethod<T, K> {
	private String one, two;
	private T foo;
	private K bar;

	private int some;

	@lombok.Builder(toBuilder = true)
	public static <Z> BuilderWithToBuilderOnMethod<Z, String> test(String one, @lombok.Builder.ObtainVia(field = "foo") Z bar) {
		return new BuilderWithToBuilderOnMethod<Z, String>();
	}
}
