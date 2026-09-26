import java.util.*;
public class ValOutersWithGenerics<Z> {
	class Inner {
	}
	class InnerWithGenerics<H> {
	}
	public void testOutersWithGenerics() {
		final java.lang.String foo = "";
		List<Inner> list = new ArrayList<Inner>();
		final ValOutersWithGenerics<Z>.Inner elem = list.get(0);
	}
	public void testLocalClasses() {
		class Local<A> {
		}
		final Local<java.lang.String> q = new Local<String>();
	}
	public static void test() {
		final ValOutersWithGenerics<java.lang.String> outer = new ValOutersWithGenerics<String>();
		final ValOutersWithGenerics<java.lang.String>.Inner inner1 = outer.new Inner();
		final ValOutersWithGenerics<java.lang.String>.InnerWithGenerics<java.lang.Integer> inner2 = outer.new InnerWithGenerics<Integer>();
	}
	static class SubClass extends ValOutersWithGenerics<String> {
		public void testSubClassOfOutersWithGenerics() {
			List<Inner> list = new ArrayList<Inner>();
			final ValOutersWithGenerics<java.lang.String>.Inner elem = list.get(0);
		}
	}
	public static void loop(Map<String, String> map) {
		for (final java.util.Map.Entry<java.lang.String, java.lang.String> e : map.entrySet()) {
		}
	}
}
