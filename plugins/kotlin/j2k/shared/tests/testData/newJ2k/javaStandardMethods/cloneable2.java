interface Cloneable {}

class C1 implements Cloneable, java.lang.Cloneable {
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}

class C2 extends C1 implements Cloneable, java.lang.Cloneable {
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}