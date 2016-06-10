package de.plushnikov.builder.generic.util;

import lombok.Singular;

import java.util.Collection;

@lombok.Builder
public class SingularCollection<T> {
	@Singular private Collection rawTypes;
	@Singular private Collection<Integer> integers;
	@Singular private Collection<T> generics;
	@Singular private Collection<? extends Number> extendsGenerics;

	public static void main(String[] args) {
		SingularCollection2.<Float>builder().generic(20.0f).integer(10).extendsGeneric(2).rawType(2.0);
	}
}
