// IGNORE_K2
public enum E {
    A,

    B {
        @Override
        void bar() {
        }
    };

    void bar(){}
}