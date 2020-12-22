package de.plushnikov.builder.generic.util.collection;

import lombok.Singular;

import java.util.List;

@lombok.Builder
public class SingularList<T> {
	@Singular private List rawTypes;
	@Singular private List<Integer> integers;
	@Singular private List<T> generics;
	@Singular private List<? extends Number> extendsGenerics;

	public static void main(String[] args) {
		SingularList.<Float>builder().generic(20.0f).integer(10).extendsGeneric(2).rawType(2.0);
	}
}
