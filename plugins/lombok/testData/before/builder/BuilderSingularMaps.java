//FORMAT: javaLangAsFQN = skip
//FORMAT: generated = skip
//FORMAT: finalParams = skip
import java.util.Map;
import java.util.SortedMap;

import lombok.Singular;

@lombok.Builder
class BuilderSingularMaps<K, V> {
	@Singular private Map<K, V> women;
	@Singular private SortedMap<K, ? extends Number> men;
	@SuppressWarnings("all") @Singular("rawMap") private Map rawMap;
	@Singular("stringMap") private Map<String, V> stringMap;
}
