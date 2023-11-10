//platform !eclipse: Requires a 'full' eclipse with intialized workspace, and we don't (yet) have that set up properly in the test run.
public class DelegateGenerics<T> {
	@lombok.experimental.Delegate
	I1<T> target;
}

interface I1<T> extends I2<T, Integer, String> {
}
interface I2<A, T, I> extends I3<Integer, I, A> {
}
interface I3<T, I, A> {
	public T t(T t);
	public I i(I a);
	public A a(A a);
}