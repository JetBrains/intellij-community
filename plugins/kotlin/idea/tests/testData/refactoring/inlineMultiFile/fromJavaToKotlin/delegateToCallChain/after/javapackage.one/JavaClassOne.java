package javapackage.one;

import javapackage.two.JavaClassTwo;

public class JavaClassOne {

    public Integer otherMethod() {
        return 42;
    }

    public JavaClassTwo toJavaClassTwo() {
        return new JavaClassTwo();
    }
}