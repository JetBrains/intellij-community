import org.jetbrains.annotations.Nullable;

class A {
    int field = foo();

    @Nullable int foo() { return 1; }
}
