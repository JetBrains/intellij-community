import lombok.Singular;

import java.util.SortedSet;

@lombok.Builder
public class SingularSortedSet<T> {
	@Singular private SortedSet rawTypes;
	@Singular private SortedSet<Integer> integers;
	@Singular private SortedSet<T> generics;
	@Singular private SortedSet<? extends Number> extendsGenerics;

}
