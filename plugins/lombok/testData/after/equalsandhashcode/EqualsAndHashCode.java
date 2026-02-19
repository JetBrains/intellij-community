class EqualsAndHashCode {
	int x;
	boolean[] y;
	Object[] z;
	String a;
	String b;
	@Override
	@SuppressWarnings("all")
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (!(o instanceof EqualsAndHashCode)) return false;
		final EqualsAndHashCode other = (EqualsAndHashCode) o;
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
	protected boolean canEqual(final Object other) {
		return other instanceof EqualsAndHashCode;
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

final class EqualsAndHashCode2 {
	int x;
	long y;
	float f;
	double d;
	boolean b;
	@Override
	@SuppressWarnings("all")
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (!(o instanceof EqualsAndHashCode2)) return false;
		final EqualsAndHashCode2 other = (EqualsAndHashCode2) o;
		if (this.x != other.x) return false;
		if (this.y != other.y) return false;
		if (Float.compare(this.f, other.f) != 0) return false;
		if (Double.compare(this.d, other.d) != 0) return false;
		if (this.b != other.b) return false;
		return true;
	}
	@Override
	@SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		result = result * PRIME + this.x;
		final long $y = this.y;
		result = result * PRIME + (int) ($y >>> 32 ^ $y);
		result = result * PRIME + Float.floatToIntBits(this.f);
		final long $d = Double.doubleToLongBits(this.d);
		result = result * PRIME + (int) ($d >>> 32 ^ $d);
		result = result * PRIME + (this.b ? 79 : 97);
		return result;
	}
}

final class EqualsAndHashCode3 extends EqualsAndHashCode {
	@Override
	@SuppressWarnings("all")
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (!(o instanceof EqualsAndHashCode3)) return false;
		final EqualsAndHashCode3 other = (EqualsAndHashCode3) o;
		if (!other.canEqual((Object) this)) return false;
		return true;
	}
	@SuppressWarnings("all")
	protected boolean canEqual(final Object other) {
		return other instanceof EqualsAndHashCode3;
	}
	@Override
	@SuppressWarnings("all")
	public int hashCode() {
		int result = 1;
		return result;
	}
}

class EqualsAndHashCode4 extends EqualsAndHashCode {
	@Override
	@SuppressWarnings("all")
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (!(o instanceof EqualsAndHashCode4)) return false;
		final EqualsAndHashCode4 other = (EqualsAndHashCode4) o;
		if (!other.canEqual((Object) this)) return false;
		if (!super.equals(o)) return false;
		return true;
	}
	@SuppressWarnings("all")
	protected boolean canEqual(final Object other) {
		return other instanceof EqualsAndHashCode4;
	}
	@Override
	@SuppressWarnings("all")
	public int hashCode() {
		int result = super.hashCode();
		return result;
	}
}
