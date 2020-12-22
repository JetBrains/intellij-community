package de.plushnikov.builder.generic.util.collection;

import lombok.Singular;

import java.util.Set;

@lombok.Builder
public class SingularSet<T> {
	@Singular private Set rawTypes;
	@Singular private Set<Integer> integers;
	@Singular private Set<T> generics;
	@Singular private Set<? extends Number> extendsGenerics;

	public static void main(String[] args) {
		SingularSet2.<Float>builder().generic(20.0f).integer(10).extendsGeneric(2).rawType(2.0);
	}
}
