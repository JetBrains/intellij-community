import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.ImmutableTable;

import lombok.Singular;

@lombok.Builder
class BuilderSingularGuavaListsSets<T> {
	@Singular private ImmutableList<T> cards;
	@Singular private ImmutableCollection<? extends Number> frogs;
	@SuppressWarnings("all") @Singular("rawSet") private ImmutableSet rawSet;
	@Singular private ImmutableSortedSet<String> passes;
	@Singular private ImmutableTable<? extends Number, ? extends Number, String> users;
}
