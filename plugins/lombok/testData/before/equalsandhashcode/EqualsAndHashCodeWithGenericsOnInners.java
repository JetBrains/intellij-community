public class EqualsAndHashCodeWithGenericsOnInners<A> {
	@lombok.EqualsAndHashCode class Inner<B> {
		int x;
	}
}

