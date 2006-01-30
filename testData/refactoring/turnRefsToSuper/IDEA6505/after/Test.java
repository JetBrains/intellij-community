interface IA {
}

interface IB {
}


class Impl implements IA, IB {
    public static IA createInstance() {
        final Impl instance = new Impl();
        return instance;
    }
}