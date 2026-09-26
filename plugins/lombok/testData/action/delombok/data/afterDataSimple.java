public class DataSimple {
    final int finalX;
    int x;
    float f;
    double d;
    boolean bool;
    boolean[] y;
    Object[] z;
    String a;

    public DataSimple(int finalX) {
        this.finalX = finalX;
    }

    public int getFinalX() {
        return this.finalX;
    }

    public int getX() {
        return this.x;
    }

    public float getF() {
        return this.f;
    }

    public double getD() {
        return this.d;
    }

    public boolean isBool() {
        return this.bool;
    }

    public boolean[] getY() {
        return this.y;
    }

    public Object[] getZ() {
        return this.z;
    }

    public String getA() {
        return this.a;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setF(float f) {
        this.f = f;
    }

    public void setD(double d) {
        this.d = d;
    }

    public void setBool(boolean bool) {
        this.bool = bool;
    }

    public void setY(boolean[] y) {
        this.y = y;
    }

    public void setZ(Object[] z) {
        this.z = z;
    }

    public void setA(String a) {
        this.a = a;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof DataSimple)) return false;
        final DataSimple other = (DataSimple) o;
        if (!other.canEqual((Object) this)) return false;
        if (this.getFinalX() != other.getFinalX()) return false;
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
        return other instanceof DataSimple;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + this.getFinalX();
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

    public String toString() {
        return "DataSimple(finalX=" + this.getFinalX() + ", x=" + this.getX() + ", f=" + this.getF() + ", d=" + this.getD() + ", bool=" + this.isBool() + ", y=" + java.util.Arrays.toString(this.getY()) + ", z=" + java.util.Arrays.deepToString(this.getZ()) + ", a=" + this.getA() + ")";
    }
}
