package de.plushnikov.val;

import lombok.val;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.RandomAccess;
import java.util.Vector;

public class ValWeirdTypes<Z> {
	private List<Z> fieldList;
	
	public void testGenerics() {
		List<String> list = new ArrayList<String>();
		list.add("Hello, World!");
		val shouldBeString = list.get(0);
		val shouldBeListOfString = list;
		val shouldBeListOfStringToo = Arrays.asList("hello", "world");
		val shouldBeString2 = shouldBeListOfString.get(0);
	}

	public void testGenericsInference() {
		val huh = Collections.emptyList();
		System.out.println(huh);
		val huh2 =  Collections.<Number>emptyList();
		System.out.println(huh2);
	}

	public void testPrimitives() {
		val x = 10;
		val y = 5 + 3L;
	}

	public void testAnonymousInnerClass() {
		val y = new Runnable() {
			public void run() {}
		};
		System.out.println(y);
	}

	public <T extends Number> void testTypeParams(List<T> param) {
		val t = param.get(0);
		val z = fieldList.get(0);
		val k = param;
		val y = fieldList;
	}

	public void testBounds(List<? extends Number> lower, List<? super Number> upper) {
		val a = lower.get(0);
		val b = upper.get(0);
		val c = lower;
		val d = upper;
		List<?> unbound = lower;
		val e = unbound;
	}

	public void testCompound() {
		val a = new ArrayList<String>();
		val b = new Vector<String>();
		val c = 1 < System.currentTimeMillis();
		val d = c ? a : b;
		RandomAccess confirm = c ? a : b;
	}

	public void nullType() {
		val nully = null;
	}

	public void testArrays() {
		val intArray = new int[] {1, 2, 3};
		val multiDimArray = new Object[][] {{}};
		val copy = intArray;
		val single = multiDimArray[0];
		val singleInt = copy[0];
	}
}