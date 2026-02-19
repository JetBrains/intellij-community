//VERSION 8:

import lombok.NonNull;
import lombok.Singular;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.Set;

@Target(ElementType.TYPE_USE)
@interface MyAnnotation {}

@lombok.experimental.SuperBuilder
class SuperBuilderSingularAnnotatedTypes {
	@Singular
  private Set<@MyAnnotation @NonNull String> foos;
	@Singular
  private Map<@MyAnnotation @NonNull String, @MyAnnotation @NonNull Integer> bars;
}
