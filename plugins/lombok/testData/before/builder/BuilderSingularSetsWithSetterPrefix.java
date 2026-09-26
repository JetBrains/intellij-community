import java.util.Set;
import java.util.SortedSet;

import lombok.Singular;

@lombok.Builder(setterPrefix = "set")
class BuilderSingularSetsWithSetterPrefix<T> {
	@Singular private Set<T> dangerMice;
	@Singular private SortedSet<? extends Number> octopodes;
	@SuppressWarnings("all") @Singular("rawSet") private Set rawSet;
	@Singular("stringSet") private Set<String> stringSet;
}
