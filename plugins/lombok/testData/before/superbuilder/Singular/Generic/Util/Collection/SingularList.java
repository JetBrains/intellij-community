import lombok.Singular;

import java.util.List;

@lombok.experimental.SuperBuilder
public class SingularList<T> {
	@Singular private List rawTypes;
	@Singular private List<Integer> integers;
	@Singular private List<T> generics;
	@Singular private List<? extends Number> extendsGenerics;

}
