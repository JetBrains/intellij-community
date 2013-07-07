import java.util.*;
import lombok.val;

public class ValOutersWithGenerics<Z> {
	
	class Inner {
	}
	
	class InnerWithGenerics<H> {
	}
	
	public void testOutersWithGenerics() {
		val foo = "";
		List<Inner> list = new ArrayList<Inner>();
		val elem = list.get(0);
	}
	
	public void testLocalClasses() {
		class Local<A> {}
		
		val q = new Local<String>();
	}
	
	public static void test() {
		val outer = new ValOutersWithGenerics<String>();
		val inner1 = outer.new Inner();
		val inner2 = outer.new InnerWithGenerics<Integer>();
	}
	
	static class SubClass extends ValOutersWithGenerics<String> {
		public void testSubClassOfOutersWithGenerics() {
			List<Inner> list = new ArrayList<Inner>();
			val elem = list.get(0);
		}
	}
	
	public static void loop(Map<String, String> map) {
		for (val e : map.entrySet()) {
		}
	}
}
