import lombok.Singular;

import java.util.Set;

@lombok.experimental.SuperBuilder
public class SingularSet<T> {
	@Singular private Set rawTypes;
	@Singular private Set<Integer> integers;
	@Singular private Set<T> generics;
	@Singular private Set<? extends Number> extendsGenerics;

}
