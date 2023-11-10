//platform !eclipse: Requires a 'full' eclipse with intialized workspace, and we don't (yet) have that set up properly in the test run.
public class DelegateAlreadyImplemented<T> {
	
	@lombok.experimental.Delegate
	private A<Integer, T> a;
	
	public void a() {
	}
	
	public void b(java.util.List<String> l) {
	}
	
	public void c(java.util.List<Integer> l, String[] a, Integer... varargs) {
	}
	
	public void d(String[][][][] d) {
	}
	
	public <Y> void e(Y x) {
	}
	
	@SuppressWarnings("unchecked")
	public void f(T s, java.util.List<T> l, T[] a, T... varargs) {
	}
	
	public void g(Number g) {
	}
}

interface A<T, T2> {
	public void a();
	
	public void b(java.util.List<T> l);
	
	@SuppressWarnings("unchecked")
	public void c(java.util.List<T> l, String[] a, T... varargs);
	
	public void d(String[][][][] d);
	
	public <X> X e(X x);
	
	@SuppressWarnings("unchecked")
	public void f(T2 s, java.util.List<T2> l, T2[] a, T2... varargs);
	
	public <G extends Number> void g(G g);
}