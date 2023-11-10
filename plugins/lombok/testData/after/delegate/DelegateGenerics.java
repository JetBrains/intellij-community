public class DelegateGenerics<T> {
	I1<T> target;

	@java.lang.SuppressWarnings("all")
	public java.lang.Integer t(final java.lang.Integer t) {
		return this.target.t(t);
	}

	@java.lang.SuppressWarnings("all")
	public java.lang.String i(final java.lang.String a) {
		return this.target.i(a);
	}

	@java.lang.SuppressWarnings("all")
	public T a(final T a) {
		return this.target.a(a);
	}
}

interface I1<T> extends I2<T, Integer, String> {
}

interface I2<A, T, I> extends I3<Integer, I, A> {
}

interface I3<T, I, A> {
	T t(T t);

	I i(I a);

	A a(A a);
}