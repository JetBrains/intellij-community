@interface Nullable {
}

class EqualsAndHashCodeWithOnParam {
	int x;
	boolean[] y;
	Object[] z;
	String a;
	String b;
	@Override
	@SuppressWarnings("all")
	public boolean equals(@Nullable final Object o) {
		if (o == this) return true;
		if (!(o instanceof EqualsAndHashCodeWithOnParam)) return false;
		final EqualsAndHashCodeWithOnParam other = (EqualsAndHashCodeWithOnParam) o;
		if (!other.canEqual((Object) this)) return false;
		if (this.x != other.x) return false;
		if (!java.util.Arrays.equals(this.y, other.y)) return false;
		if (!java.util.Arrays.deepEquals(this.z, other.z)) return false;
		final Object this$a = this.a;
		final Object other$a = other.a;
		if (this$a == null ? other$a != null : !this$a.equals(other$a)) return false;
		final Object this$b = this.b;
		final Object other$b = other.b;
		if (this$b == null ? other$b != null : !this$b.equals(other$b)) return false;
		return true;
	}
	@SuppressWarnings("all")
	protected boolean canEqual(@Nullable final Object other) {
		return other instanceof EqualsAndHashCodeWithOnParam;
	}
	@Override
	@SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		result = result * PRIME + this.x;
		result = result * PRIME + java.util.Arrays.hashCode(this.y);
		result = result * PRIME + java.util.Arrays.deepHashCode(this.z);
		final Object $a = this.a;
		result = result * PRIME + ($a == null ? 43 : $a.hashCode());
		final Object $b = this.b;
		result = result * PRIME + ($b == null ? 43 : $b.hashCode());
		return result;
	}
}
