import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

interface J<A, B> extends T<A, B> {
    @Override
    <C> @Nullable U<C> foofoofoo(@NotNull List<? extends C> a, @Nullable A b, @NotNull U<B> c);
}

abstract class J1<X, Y> implements J<U<X>, U<Y>> {
    @Override
    public <C> @Nullable U<C> foofoofoo(@NotNull List<? extends C> xu, @Nullable U<X> yu, @NotNull U<U<Y>> c) {
        throw new UnsupportedOperationException();
    }
}

abstract class J2<X> extends J1<X, String> {
    @Override
    public <C> @Nullable U<C> foofoofoo(@NotNull List<? extends C> xu, @Nullable U<X> stringU, @NotNull U<U<String>> c) {
        throw new UnsupportedOperationException();
    }
}

class J3 extends J2<Object> {
    @Override
    public <D> @Nullable U<D> foofoofoo(@NotNull List<? extends D> objectU, @Nullable U<Object> stringU, @NotNull U<U<String>> c) {
        throw new UnsupportedOperationException();
    }
}
