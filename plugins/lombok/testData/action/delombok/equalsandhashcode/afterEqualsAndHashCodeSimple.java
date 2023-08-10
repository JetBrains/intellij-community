class EqualsAndHashCodeSimple {
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
        if (!(o instanceof EqualsAndHashCodeSimple)) return false;
        final EqualsAndHashCodeSimple other = (EqualsAndHashCodeSimple) o;
        if (!other.canEqual((Object) this)) return false;
        if (this.getX() != other.getX()) return false;
        if (Float.compare(this.getF(), other.getF()) != 0) return false;
        if (Double.compare(this.getD(), other.getD()) != 0) return false;
        if (this.isBool() != other.isBool()) return false;
        if (!java.util.Arrays.equals(this.getY(), other.getY())) return false;
        if (!java.util.Arrays.deepEquals(this.getZ(), other.getZ())) return false;
        final Object this$a = this.getA();
        final Object other$a = other.getA();
        if (this$a == null ? other$a != null : !this$a.equals(other$a)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof EqualsAndHashCodeSimple;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + this.getX();
        result = result * PRIME + Float.floatToIntBits(this.getF());
        final long $d = Double.doubleToLongBits(this.getD());
        result = result * PRIME + (int) ($d >>> 32 ^ $d);
        result = result * PRIME + (this.isBool() ? 79 : 97);
        result = result * PRIME + java.util.Arrays.hashCode(this.getY());
        result = result * PRIME + java.util.Arrays.deepHashCode(this.getZ());
        final Object $a = this.getA();
        result = result * PRIME + ($a == null ? 43 : $a.hashCode());
        return result;
    }
}
