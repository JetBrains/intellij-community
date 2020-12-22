package de.plushnikov.builder.generic.util.collection;

import lombok.Singular;

import java.util.NavigableSet;

@lombok.Builder
public class SingularNavigableSet<T> {
	@Singular private NavigableSet rawTypes;
	@Singular private NavigableSet <Integer> integers;
	@Singular private NavigableSet <T> generics;
	@Singular private NavigableSet <? extends Number> extendsGenerics;

	public static void main(String[] args) {
		SingularNavigableSet2.<Float>builder().generic(20.0f).integer(10).extendsGeneric(2).rawType(2.0);
	}
}
