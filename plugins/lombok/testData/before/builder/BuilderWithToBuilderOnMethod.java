import lombok.Builder;

class BuilderWithToBuilderOnMethod<T, K> {
	private String one, two;
	private T foo;
	private K bar;

	private int some;

	@Builder(toBuilder = true)
	public static <Z> BuilderWithToBuilderOnMethod<Z, String> test(String one, @Builder.ObtainVia(field = "foo") Z bar) {
		return new BuilderWithToBuilderOnMethod<Z, String>();
	}
}
