class EqualsAndHashCodeConfigKeys1Parent {
	@Override
	@SuppressWarnings("all")
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (!(o instanceof EqualsAndHashCodeConfigKeys1Parent)) return false;
		final EqualsAndHashCodeConfigKeys1Parent other = (EqualsAndHashCodeConfigKeys1Parent) o;
		if (!other.canEqual((Object) this)) return false;
		return true;
	}
	@SuppressWarnings("all")
	protected boolean canEqual(final Object other) {
		return other instanceof EqualsAndHashCodeConfigKeys1Parent;
	}
	@Override
	@SuppressWarnings("all")
	public int hashCode() {
		int result = 1;
		return result;
	}
}

class EqualsAndHashCodeConfigKeys1 extends EqualsAndHashCodeConfigKeys1Parent {
	int x;
	@Override
	@SuppressWarnings("all")
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (!(o instanceof EqualsAndHashCodeConfigKeys1)) return false;
		final EqualsAndHashCodeConfigKeys1 other = (EqualsAndHashCodeConfigKeys1) o;
		if (!other.canEqual((Object) this)) return false;
		if (this.x != other.x) return false;
		return true;
	}
	@SuppressWarnings("all")
	protected boolean canEqual(final Object other) {
		return other instanceof EqualsAndHashCodeConfigKeys1;
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
