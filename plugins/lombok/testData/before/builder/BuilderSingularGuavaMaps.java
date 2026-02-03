import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSortedMap;

import lombok.Singular;

@lombok.Builder
class BuilderSingularGuavaMaps<K, V> {
	@Singular private ImmutableMap<K, V> battleaxes;
	@Singular private ImmutableSortedMap<Integer, ? extends V> vertices;
	@SuppressWarnings("all") @Singular("rawMap") private ImmutableBiMap rawMap;
}
