import lombok.Builder;

public class Foo {
    public static class camelCaseKlass {
        public String value = "OK";
    }

    @Builder(builderMethodName = "klassBuilder")
    camelCaseKlass initWithCustomClass(camelCaseKlass klass) {
        return klass;
    }
}

class TestJava {
    String test() {
        Foo foo = new Foo();

        Foo.camelCaseKlassBuilder klassBuilder = foo.klassBuilder();
        if (!"OK".equals(klassBuilder.klass(new Foo.camelCaseKlass()).build().value)) {
            return "FAIL";
        }

        return "OK";
    }
}
