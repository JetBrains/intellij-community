class C1 {
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}

class C2 implements Cloneable {
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}

class C3 extends C2 {
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}

class C4 extends C2 implements Cloneable {
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}