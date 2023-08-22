//CONF: lombok.singular.useGuava = true
import java.util.Set;
import java.util.NavigableMap;
import java.util.Collection;

import lombok.Singular;

@lombok.Builder
class BuilderSingularRedirectToGuava {
	@Singular private Set<String> dangerMice;
	@Singular private NavigableMap<Integer, Number> things;
	@Singular private Collection<Class<?>> doohickeys;
}
