import lombok.Singular;

import java.util.NavigableSet;

@lombok.Builder
public class SingularNavigableSet<T> {
	@Singular private NavigableSet rawTypes;
	@Singular private NavigableSet <Integer> integers;
	@Singular private NavigableSet <T> generics;
	@Singular private NavigableSet <? extends Number> extendsGenerics;

}
