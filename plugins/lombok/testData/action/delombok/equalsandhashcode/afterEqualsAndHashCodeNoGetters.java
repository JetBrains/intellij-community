class EqualsAndHashCodeNoGetters {
    int x;
    float f;
    double d;
    boolean bool;
    boolean[] y;
    Object[] z;
    String a;

    int getX() {
        return x;
    }

    float getF() {
        return f;
    }

    double getD() {
        return d;
    }

    boolean isBool() {
        return bool;
    }

    boolean[] getY() {
        return y;
    }

    Object[] getZ() {
        return z;
    }

    String getA() {
        return a;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof EqualsAndHashCodeNoGetters)) return false;
        final EqualsAndHashCodeNoGetters other = (EqualsAndHashCodeNoGetters) o;
        if (!other.canEqual((Object) this)) return false;
        if (this.x != other.x) return false;
        if (Float.compare(this.f, other.f) != 0) return false;
        if (Double.compare(this.d, other.d) != 0) return false;
        if (this.bool != other.bool) return false;
        if (!java.util.Arrays.equals(this.y, other.y)) return false;
        if (!java.util.Arrays.deepEquals(this.z, other.z)) return false;
        final Object this$a = this.a;
        final Object other$a = other.a;
        if (this$a == null ? other$a != null : !this$a.equals(other$a)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof EqualsAndHashCodeNoGetters;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + this.x;
        result = result * PRIME + Float.floatToIntBits(this.f);
        final long $d = Double.doubleToLongBits(this.d);
        result = result * PRIME + (int) ($d >>> 32 ^ $d);
        result = result * PRIME + (this.bool ? 79 : 97);
        result = result * PRIME + java.util.Arrays.hashCode(this.y);
        result = result * PRIME + java.util.Arrays.deepHashCode(this.z);
        final Object $a = this.a;
        result = result * PRIME + ($a == null ? 43 : $a.hashCode());
        return result;
    }
}
