class EqualsAndHashCodeExplicitInclude {
	int x;
	@Override
	@SuppressWarnings("all")
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (!(o instanceof EqualsAndHashCodeExplicitInclude)) return false;
		final EqualsAndHashCodeExplicitInclude other = (EqualsAndHashCodeExplicitInclude) o;
		if (!other.canEqual((Object) this)) return false;
		return true;
	}
	@SuppressWarnings("all")
	protected boolean canEqual(final Object other) {
		return other instanceof EqualsAndHashCodeExplicitInclude;
	}
	@Override
	@SuppressWarnings("all")
	public int hashCode() {
		int result = 1;
		return result;
	}
}
