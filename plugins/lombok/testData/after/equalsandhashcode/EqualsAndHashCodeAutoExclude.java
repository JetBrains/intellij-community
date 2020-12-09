class EqualsAndHashCodeAutoExclude {
	int x;
	String $a;
	transient String b;
	@Override
	@SuppressWarnings("all")
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (!(o instanceof EqualsAndHashCodeAutoExclude)) return false;
		final EqualsAndHashCodeAutoExclude other = (EqualsAndHashCodeAutoExclude) o;
		if (!other.canEqual((Object) this)) return false;
		if (this.x != other.x) return false;
		return true;
	}
	@SuppressWarnings("all")
	protected boolean canEqual(final Object other) {
		return other instanceof EqualsAndHashCodeAutoExclude;
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

class EqualsAndHashCodeAutoExclude2 {
	int x;
	String $a;
	transient String b;
	@Override
	@SuppressWarnings("all")
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (!(o instanceof EqualsAndHashCodeAutoExclude2)) return false;
		final EqualsAndHashCodeAutoExclude2 other = (EqualsAndHashCodeAutoExclude2) o;
		if (!other.canEqual((Object) this)) return false;
		if (this.x != other.x) return false;
		final Object this$$a = this.$a;
		final Object other$$a = other.$a;
		if (this$$a == null ? other$$a != null : !this$$a.equals(other$$a)) return false;
		final Object this$b = this.b;
		final Object other$b = other.b;
		if (this$b == null ? other$b != null : !this$b.equals(other$b)) return false;
		return true;
	}
	@SuppressWarnings("all")
	protected boolean canEqual(final Object other) {
		return other instanceof EqualsAndHashCodeAutoExclude2;
	}
	@Override
	@SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		result = result * PRIME + this.x;
		final Object $$a = this.$a;
		result = result * PRIME + ($$a == null ? 43 : $$a.hashCode());
		final Object $b = this.b;
		result = result * PRIME + ($b == null ? 43 : $b.hashCode());
		return result;
	}
}
