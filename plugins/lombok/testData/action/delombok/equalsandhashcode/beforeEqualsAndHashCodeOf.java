@lombok.EqualsAndHashCode(of = "x")
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
}
