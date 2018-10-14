//version 7:
public class EqualsAndHashCodeWithGenericsOnInners<A> {
	class Inner<B> {
		int x;
		@Override
		@SuppressWarnings("all")
    // @ME CHANGED FROM EqualsAndHashCodeWithGenericsOnInners<?>.Inner<?>  to EqualsAndHashCodeWithGenericsOnInners<A>.Inner<?>
		public boolean equals(final Object o) {
			if (o == this) return true;
			if (!(o instanceof EqualsAndHashCodeWithGenericsOnInners.Inner)) return false;
			final EqualsAndHashCodeWithGenericsOnInners<A>.Inner<?> other = (EqualsAndHashCodeWithGenericsOnInners<A>.Inner<?>) o;
			if (!other.canEqual((Object) this)) return false;
			if (this.x != other.x) return false;
			return true;
		}
		@SuppressWarnings("all")
		protected boolean canEqual(final Object other) {
			return other instanceof EqualsAndHashCodeWithGenericsOnInners.Inner;
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

