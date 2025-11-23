public class Foo {
    public static class camelCaseKlass {
        public String value = "OK";
    }

    camelCaseKlass initWithCustomClass(camelCaseKlass klass) {
        return klass;
    }

    public class camelCaseKlassBuilder {
        private Foo.camelCaseKlass klass;

        camelCaseKlassBuilder() {
        }

        public Foo.camelCaseKlassBuilder klass(Foo.camelCaseKlass klass) {
            this.klass = klass;
            return this;
        }

        public Foo.camelCaseKlass build() {
            return Foo.this.initWithCustomClass(this.klass);
        }

        public String toString() {
            return "Foo.camelCaseKlassBuilder(klass=" + this.klass + ")";
        }
    }

    public camelCaseKlassBuilder klassBuilder() {
        return new Foo.camelCaseKlassBuilder();
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
