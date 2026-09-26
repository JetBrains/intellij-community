import lombok.Singular;

@lombok.experimental.SuperBuilder
public class SingularIterable<T> {
	@Singular private Iterable rawTypes;
	@Singular private Iterable<Integer> integers;
	@Singular private Iterable<T> generics;
	@Singular private Iterable<? extends Number> extendsGenerics;
}
