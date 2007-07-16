import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

class B {
    public final A myDelegate = new A();

    @Nullable
    public Object methodFromA(@NotNull String s) {
        return myDelegate.methodFromA(s);
    }
}