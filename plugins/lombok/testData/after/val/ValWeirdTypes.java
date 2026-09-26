import java.util.*;
public class ValWeirdTypes<Z> {
	private List<Z> fieldList;
	public void testGenerics() {
		List<String> list = new ArrayList<String>();
		list.add("Hello, World!");
		final java.lang.String shouldBeString = list.get(0);
		final java.util.List<java.lang.String> shouldBeListOfString = list;
		final java.util.List<java.lang.String> shouldBeListOfStringToo = Arrays.asList("hello", "world");
		final java.lang.String shouldBeString2 = shouldBeListOfString.get(0);
	}
	public void testGenericsInference() {
		final java.util.List<java.lang.Object> huh = Collections.emptyList();
		final java.util.List<java.lang.Number> huh2 = Collections.<Number>emptyList();
	}
	public void testPrimitives() {
		final int x = 10;
		final long y = 5 + 3L;
	}
	public void testAnonymousInnerClass() {
		final java.lang.Runnable y = new Runnable(){
			public void run() {
			}
		};
	}
	public <T extends Number> void testTypeParams(List<T> param) {
		final T t = param.get(0);
		final Z z = fieldList.get(0);
		final java.util.List<T> k = param;
		final java.util.List<Z> y = fieldList;
	}
	public void testBounds(List<? extends Number> lower, List<? super Number> upper) {
		final java.lang.Number a = lower.get(0);
		final java.lang.Object b = upper.get(0);
		final java.util.List<? extends java.lang.Number> c = lower;
		final java.util.List<? super java.lang.Number> d = upper;
		List<?> unbound = lower;
		final java.util.List<?> e = unbound;
	}
	public void testCompound() {
		final java.util.ArrayList<java.lang.String> a = new ArrayList<String>();
		final java.util.Vector<java.lang.String> b = new Vector<String>();
		final boolean c = 1 < System.currentTimeMillis();
		final java.util.AbstractList<java.lang.String> d = c ? a : b;
		java.util.RandomAccess confirm = c ? a : b;
	}
	public void nullType() {
		final java.lang.Object nully = null;
	}
	public void testArrays() {
		final int[] intArray = new int[]{1, 2, 3};
		final java.lang.Object[][] multiDimArray = new Object[][]{{}};
		final int[] copy = intArray;
		final java.lang.Object[] single = multiDimArray[0];
		final int singleInt = copy[0];
	}
}