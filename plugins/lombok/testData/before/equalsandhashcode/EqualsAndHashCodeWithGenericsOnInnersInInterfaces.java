public interface EqualsAndHashCodeWithGenericsOnInnersInInterfaces<A> {
	@lombok.EqualsAndHashCode class Inner<B> {
		int x;
	}
}

