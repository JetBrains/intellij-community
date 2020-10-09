import lombok.Singular;

import java.util.NavigableSet;

@lombok.experimental.SuperBuilder
public class SingularNavigableSet<T> {
	@Singular private NavigableSet rawTypes;
	@Singular private NavigableSet <Integer> integers;
	@Singular private NavigableSet <T> generics;
	@Singular private NavigableSet <? extends Number> extendsGenerics;

}
