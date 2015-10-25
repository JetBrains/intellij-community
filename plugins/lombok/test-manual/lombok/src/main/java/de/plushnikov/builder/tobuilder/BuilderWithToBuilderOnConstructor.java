package de.plushnikov.builder.tobuilder;

@lombok.experimental.Accessors(prefix = "m")
class BuilderWithToBuilderOnConstructor<T> {
	private String mOne, mTwo;

	private T foo;

	@lombok.Singular
	private java.util.List<T> bars;

	@lombok.Builder(toBuilder = true)
	public BuilderWithToBuilderOnConstructor(String mOne, @lombok.Builder.ObtainVia(field = "foo") T bar) {
	}
}
