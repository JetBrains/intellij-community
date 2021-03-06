class EqualsAndHashCodeWithExistingMethods {
	int x;
	public int hashCode() {
		return 42;
	}
}

final class EqualsAndHashCodeWithExistingMethods2 {
	int x;
	public boolean equals(Object other) {
		return false;
	}
}

final class EqualsAndHashCodeWithExistingMethods3 extends EqualsAndHashCodeWithExistingMethods {
	int x;
	private boolean canEqual(Object other) {
		return true;
	}
	@Override
	@SuppressWarnings("all")
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (!(o instanceof EqualsAndHashCodeWithExistingMethods3)) return false;
		final EqualsAndHashCodeWithExistingMethods3 other = (EqualsAndHashCodeWithExistingMethods3) o;
		if (!other.canEqual((Object) this)) return false;
		if (!super.equals(o)) return false;
		if (this.x != other.x) return false;
		return true;
	}
	@Override
	@SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 59;
		int result = super.hashCode();
		result = result * PRIME + this.x;
		return result;
	}
}
