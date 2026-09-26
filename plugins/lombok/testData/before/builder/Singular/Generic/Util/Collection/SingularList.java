import lombok.Singular;

import java.util.List;

@lombok.Builder
public class SingularList<T> {
	@Singular private List rawTypes;
	@Singular private List<Integer> integers;
	@Singular private List<T> generics;
	@Singular private List<? extends Number> extendsGenerics;

}
