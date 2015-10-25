package de.plushnikov.builder.tobuilder;

@lombok.Builder(toBuilder = true)
@lombok.experimental.Accessors(prefix = "m")
public class BuilderWithToBuilderOnClass<T> {
	private String mOne, mTwo;

	@lombok.Builder.ObtainVia(method = "rrr", isStatic = true)
	private T foo;

	@lombok.Singular
	private java.util.List<T> bars;

	public static <K> K rrr(BuilderWithToBuilderOnClass<K> x) {
		return x.foo;
	}
}