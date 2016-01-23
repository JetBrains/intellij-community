class BuilderWithToBuilderOnConstructor<T> {
	private String one, two;

	private T foo;

	private int bar;

	@lombok.Builder(toBuilder = true)
	public BuilderWithToBuilderOnConstructor(String one, @lombok.Builder.ObtainVia(field = "foo") T bar) {
	}
}
