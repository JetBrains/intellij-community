// !ADD_JAVA_API
import javaApi.Anon5;
import javaApi.TypeUseAnon1;
import javaApi.TypeUseAnon2;
import javaApi.TypeUseAnon3;
import java.io.File;
import java.util.*;

public class TEST1 {
    public @Anon5(1) @TypeUseAnon1 String foo(@Anon5(2) @TypeUseAnon1 Object o) {
        @Anon5(3) @TypeUseAnon1 String baz = "";
        return "";
    }
    @Anon5(4) @TypeUseAnon1 String bar;
}

public class TEST2 {
    public @Anon5(1) @TypeUseAnon2 String foo(@Anon5(2) @TypeUseAnon2 Object o) {
        @Anon5(3) @TypeUseAnon2 String baz = "";
        return "";
    }

    @Anon5(4) @TypeUseAnon2 String bar;
}

public class TEST3 {
    public @Anon5(1)
    @TypeUseAnon3 String foo(@Anon5(2) @TypeUseAnon3 Object o) {
        @Anon5(3) @TypeUseAnon3 String baz = "";
        return "";
    }

    @Anon5(4) @TypeUseAnon3 String bar;
}

public class TestInstanceOf {
    void test(String s) {
        System.out.println(s instanceof @TypeUseAnon3 String);
    }
}

public class TestTypeCast {
    void test(Object foo) {
        String s = (@TypeUseAnon3 String) foo;
    }
}

public class TestInheritance1 extends @TypeUseAnon1 C {}
public class TestInheritance2 implements @TypeUseAnon1 I1 {}
public class TestInheritance3 extends @TypeUseAnon1 C implements @TypeUseAnon2 I1, @TypeUseAnon2 @TypeUseAnon3 I2 {}

public class C {}
public interface I1 {}
public interface I2 {}

public class TestCatch {
    void foo() {
        try {
        } catch (@TypeUseAnon1 Exception e) {
        }
    }
}

public class TestForLoopParameter {
    public void foo(int[] arr) {
        for (@TypeUseAnon1 int test : arr) {
            System.out.println(test);
        }
        for (@TypeUseAnon1 int i = 0; i < arr.length; i++) {
            System.out.println(i);
        }
    }
}

public class TestPrimaryConstructorProperty {
    @TypeUseAnon1
    private String foo;

    public TestPrimaryConstructorProperty(@TypeUseAnon2 String foo) {
        this.foo = foo;
    }
}

public class TestStandardMethods {
    @Override
    @TypeUseAnon1
    public String toString() {
        return "";
    }

    @Override
    @TypeUseAnon1
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}

public @interface TestAnnotationMethod {
    @TypeUseAnon1 String value();
    @TypeUseAnon1 @TypeUseAnon2 String value2() default "test";
}

/**
 * TYPE_USE annotation is allowed on a type parameter only in Java,
 * in Kotlin an error is expected.
 */
public interface TestTypeParameter<@TypeUseAnon1 F extends @TypeUseAnon1 File> {
    <@TypeUseAnon1 T extends @TypeUseAnon1 File> void foo();
}

public class TestTypeArgument {
    void f1() {
        this.<@TypeUseAnon1 String>f2("");
    }
    <T> void f2(T t) {
    }
}