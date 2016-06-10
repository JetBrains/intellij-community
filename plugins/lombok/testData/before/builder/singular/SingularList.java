import java.util.List;

import lombok.Singular;

@lombok.Builder
public class SingularList<T> {
	@Singular private List rawTypes;
	@Singular private List<Integer> integers;
	@Singular private List<T> generics;
	@Singular private List<? extends Number> extendsGenerics;
}
