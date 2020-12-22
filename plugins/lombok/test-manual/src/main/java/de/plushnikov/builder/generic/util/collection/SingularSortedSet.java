package de.plushnikov.builder.generic.util.collection;

import lombok.Singular;

import java.util.SortedSet;

@lombok.Builder
public class SingularSortedSet<T> {
	@Singular private SortedSet rawTypes;
	@Singular private SortedSet<Integer> integers;
	@Singular private SortedSet<T> generics;
	@Singular private SortedSet<? extends Number> extendsGenerics;

	public static void main(String[] args) {
		SingularSortedSet2.<Float>builder().generic(20.0f).integer(10).extendsGeneric(2).rawType(2.0);
	}
}
