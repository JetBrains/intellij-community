package p {

public class A {
    public function f() {
    }

    function g(): String {
    }

    function g():Boolean {
        var a = "hello";
        var b: String = "hello";
        var c: String = "123";
    }
}

class B implements I1, I2, I3 {
}

public class B implements I2, I1, I3 {
}
}