import org.jetbrains.annotations.NotNull;

interface A {
}

class B<T extends @NotNull A> {
}