import lombok.Singular;

import java.util.ArrayList;
import java.util.Collection;

@lombok.Builder
public class SingularCollection<T> {
	@Singular private Collection rawTypes;
	@Singular private Collection<Integer> integers;
	@Singular private Collection<T> generics;
	@Singular private Collection<? extends Number> extendsGenerics;

	public static void main(String[] args) {
	}
}
