class EqualsAndHashCodeSimpleOf {
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
        if (!(o instanceof EqualsAndHashCodeSimpleOf)) return false;
        final EqualsAndHashCodeSimpleOf other = (EqualsAndHashCodeSimpleOf) o;
        if (!other.canEqual((Object) this)) return false;
        if (this.getX() != other.getX()) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
    return other instanceof EqualsAndHashCodeSimpleOf;
  }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + this.getX();
        return result;
    }
}
