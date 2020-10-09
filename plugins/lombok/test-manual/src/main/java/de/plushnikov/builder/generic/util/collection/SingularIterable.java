package de.plushnikov.builder.generic.util.collection;

import lombok.Singular;

@lombok.Builder
public class SingularIterable<T> {
	@Singular private Iterable rawTypes;
	@Singular private Iterable<Integer> integers;
	@Singular private Iterable<T> generics;
	@Singular private Iterable<? extends Number> extendsGenerics;

	public static void main(String[] args) {
		SingularIterable2.<Float>builder().generic(20.0f).integer(10).extendsGeneric(2).rawType(2.0);
	}
}
