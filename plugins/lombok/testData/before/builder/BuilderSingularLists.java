import java.util.List;
import java.util.Collection;

import lombok.Singular;

@lombok.Builder
class BuilderSingularLists<T> {
	@Singular private List<T> children;
	@Singular private Collection<? extends Number> scarves;
	@SuppressWarnings("all") @Singular("rawList") private List rawList;
}
