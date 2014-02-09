public class DataSimple {
    final int finalX;
    int x;
    float f;
    double d;
    boolean bool;
    boolean[] y;
    Object[] z;
    String a;

    @java.beans.ConstructorProperties({"finalX"})
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

    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof DataSimple)) return false;
        final DataSimple other = (DataSimple) o;
        if (!other.canEqual((Object) this)) return false;
        if (this.finalX != other.finalX) return false;
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

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + this.finalX;
        result = result * PRIME + this.x;
        result = result * PRIME + Float.floatToIntBits(this.f);
        final long $d = Double.doubleToLongBits(this.d);
        result = result * PRIME + (int) ($d >>> 32 ^ $d);
        result = ((result * PRIME) + (this.bool ? 79 : 97));
        result = result * PRIME + java.util.Arrays.hashCode(this.y);
        result = result * PRIME + java.util.Arrays.deepHashCode(this.z);
        final Object $a = this.a;
        result = result * PRIME + ($a == null ? 0 : $a.hashCode());
        return result;
    }

    public boolean canEqual(Object other) {
        return other instanceof DataSimple;
    }

    public String toString() {
        return "DataSimple(finalX=" + this.finalX + ", x=" + this.x + ", f=" + this.f + ", d=" + this.d + ", bool=" + this.bool + ", y=" + java.util.Arrays.toString(this.y) + ", z=" + java.util.Arrays.deepToString(this.z) + ", a=" + this.a + ")";
    }
}
