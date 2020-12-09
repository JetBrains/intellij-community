class EqualsAndHashCodeConfigKeys2Object extends Object {
	@Override
	@SuppressWarnings("all")
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (!(o instanceof EqualsAndHashCodeConfigKeys2Object)) return false;
		final EqualsAndHashCodeConfigKeys2Object other = (EqualsAndHashCodeConfigKeys2Object) o;
		if (!other.canEqual((Object) this)) return false;
		return true;
	}
	@SuppressWarnings("all")
	protected boolean canEqual(final Object other) {
		return other instanceof EqualsAndHashCodeConfigKeys2Object;
	}
	@Override
	@SuppressWarnings("all")
	public int hashCode() {
		int result = 1;
		return result;
	}
}

class EqualsAndHashCodeConfigKeys2Parent {
	@Override
	@SuppressWarnings("all")
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (!(o instanceof EqualsAndHashCodeConfigKeys2Parent)) return false;
		final EqualsAndHashCodeConfigKeys2Parent other = (EqualsAndHashCodeConfigKeys2Parent) o;
		if (!other.canEqual((Object) this)) return false;
		return true;
	}
	@SuppressWarnings("all")
	protected boolean canEqual(final Object other) {
		return other instanceof EqualsAndHashCodeConfigKeys2Parent;
	}
	@Override
	@SuppressWarnings("all")
	public int hashCode() {
		int result = 1;
		return result;
	}
}

class EqualsAndHashCodeConfigKeys2 extends EqualsAndHashCodeConfigKeys2Parent {
	int x;
	@Override
	@SuppressWarnings("all")
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (!(o instanceof EqualsAndHashCodeConfigKeys2)) return false;
		final EqualsAndHashCodeConfigKeys2 other = (EqualsAndHashCodeConfigKeys2) o;
		if (!other.canEqual((Object) this)) return false;
		if (!super.equals(o)) return false;
		if (this.x != other.x) return false;
		return true;
	}
	@SuppressWarnings("all")
	protected boolean canEqual(final Object other) {
		return other instanceof EqualsAndHashCodeConfigKeys2;
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
