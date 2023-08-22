public interface EqualsAndHashCodeWithGenericsOnInnersInInterfaces<A> {
	class Inner<B> {
		int x;
		@Override
		@SuppressWarnings("all")
		public boolean equals(final Object o) {
			if (o == this) return true;
			if (!(o instanceof EqualsAndHashCodeWithGenericsOnInnersInInterfaces.Inner)) return false;
			final EqualsAndHashCodeWithGenericsOnInnersInInterfaces.Inner<?> other = (EqualsAndHashCodeWithGenericsOnInnersInInterfaces.Inner<?>) o;
			if (!other.canEqual((Object) this)) return false;
			if (this.x != other.x) return false;
			return true;
		}
		@SuppressWarnings("all")
		protected boolean canEqual(final Object other) {
			return other instanceof EqualsAndHashCodeWithGenericsOnInnersInInterfaces.Inner;
		}
		@Override
		@SuppressWarnings("all")
		public int hashCode() {
			final int PRIME = 59;
			int result = 1;
			result = result * PRIME + this.x;
			return result;
		}
	}
}
