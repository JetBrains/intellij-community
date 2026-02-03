public class WithByTypes<T> {
	private final int a;
	private final long b;
	private final short c;
	private final char d;
	private final byte e;
	private final double f;
	private final float g;
	private final boolean h;
	private final T i;
	public static void example() {
		new WithByTypes<String>(0, 0, (short) 0, ' ', (byte) 0, 0.0, 0.0F, true, "").withHBy(x -> !x).withFBy(x -> x + 0.5);
	}
	@java.lang.SuppressWarnings("all")
	@lombok.Generated
	public WithByTypes(final int a, final long b, final short c, final char d, final byte e, final double f, final float g, final boolean h, final T i) {
		this.a = a;
		this.b = b;
		this.c = c;
		this.d = d;
		this.e = e;
		this.f = f;
		this.g = g;
		this.h = h;
		this.i = i;
	}
	@java.lang.SuppressWarnings("all")
	@lombok.Generated
	public WithByTypes<T> withABy(final java.util.function.IntUnaryOperator transformer) {
		return new WithByTypes<T>(transformer.applyAsInt(this.a), this.b, this.c, this.d, this.e, this.f, this.g, this.h, this.i);
	}
	@java.lang.SuppressWarnings("all")
	@lombok.Generated
	public WithByTypes<T> withBBy(final java.util.function.LongUnaryOperator transformer) {
		return new WithByTypes<T>(this.a, transformer.applyAsLong(this.b), this.c, this.d, this.e, this.f, this.g, this.h, this.i);
	}
	@java.lang.SuppressWarnings("all")
	@lombok.Generated
	public WithByTypes<T> withCBy(final java.util.function.IntUnaryOperator transformer) {
		return new WithByTypes<T>(this.a, this.b, (short) transformer.applyAsInt(this.c), this.d, this.e, this.f, this.g, this.h, this.i);
	}
	@java.lang.SuppressWarnings("all")
	@lombok.Generated
	public WithByTypes<T> withDBy(final java.util.function.IntUnaryOperator transformer) {
		return new WithByTypes<T>(this.a, this.b, this.c, (char) transformer.applyAsInt(this.d), this.e, this.f, this.g, this.h, this.i);
	}
	@java.lang.SuppressWarnings("all")
	@lombok.Generated
	public WithByTypes<T> withEBy(final java.util.function.IntUnaryOperator transformer) {
		return new WithByTypes<T>(this.a, this.b, this.c, this.d, (byte) transformer.applyAsInt(this.e), this.f, this.g, this.h, this.i);
	}
	@java.lang.SuppressWarnings("all")
	@lombok.Generated
	public WithByTypes<T> withFBy(final java.util.function.DoubleUnaryOperator transformer) {
		return new WithByTypes<T>(this.a, this.b, this.c, this.d, this.e, transformer.applyAsDouble(this.f), this.g, this.h, this.i);
	}
	@java.lang.SuppressWarnings("all")
	@lombok.Generated
	public WithByTypes<T> withGBy(final java.util.function.DoubleUnaryOperator transformer) {
		return new WithByTypes<T>(this.a, this.b, this.c, this.d, this.e, this.f, (float) transformer.applyAsDouble(this.g), this.h, this.i);
	}
	@java.lang.SuppressWarnings("all")
	@lombok.Generated
	public WithByTypes<T> withHBy(final java.util.function.UnaryOperator<java.lang.Boolean> transformer) {
		return new WithByTypes<T>(this.a, this.b, this.c, this.d, this.e, this.f, this.g, transformer.apply(this.h), this.i);
	}
	@java.lang.SuppressWarnings("all")
	@lombok.Generated
	public WithByTypes<T> withIBy(final java.util.function.Function<? super T, ? extends T> transformer) {
		return new WithByTypes<T>(this.a, this.b, this.c, this.d, this.e, this.f, this.g, this.h, transformer.apply(this.i));
	}
}
