class TestBaseBase {
    void bazBaseBase(int i) {

    }
}

class TestBase extends TestBaseBase {
    void fooBase(int i) {

    }

    void barBase(int i) {

    }
}

class Test extends TestBase {
    Test() {
        foo(1);
        fooBase(1);
        this.barBase(1);
        staticMethod(1);
        bazBaseBase(1);
    }

    void foo(int i) {

    }

    static void staticMethod(int i) {

    }


    class InnerTest {
        InnerTest() {
            foo(2);
            fooBase(2);
            bar(2);
            staticMethod(2);

            TestBase t = new TestBase() {
                void fooBase(int i) {
                    bazBaseBase(7);
                    bar(7);
                    barBase(7);
                }
            };
        }

        void bar(int i) {

        }

        class InnerInnerTest {
            InnerInnerTest() {
                foo(3);
                fooBase(3);
                bar(3);
                baz(3);
                staticMethod(3);
            }

            void baz(int i) {

            }
        }
    }

    class InnerTest2 extends TestBase {
        InnerTest2() {
            foo(4);
            fooBase(4);
        }
    }

    static class StaticTest {
        StaticTest() {
            bar(5);
            staticMethod(5);
        }

        void bar(int i) {

        }

        class InnerInStatic {
            InnerInStatic() {
                bar(6);
            }
        }
    }
}