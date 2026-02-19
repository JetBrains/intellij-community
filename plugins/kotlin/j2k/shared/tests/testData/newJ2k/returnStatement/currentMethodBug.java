import org.jetbrains.annotations.Nullable;

interface I {
    int returnInt();
}

class C {
    @Nullable Object object() {
        foo(new I() {
            @Override
            public int returnInt() {
                return 0;
            }
        });
        return string;
    }

    void foo(I i) {}

    @Nullable String string;
}