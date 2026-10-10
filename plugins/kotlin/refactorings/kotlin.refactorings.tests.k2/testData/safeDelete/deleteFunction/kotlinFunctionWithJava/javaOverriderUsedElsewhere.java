class Impl extends Base {
    @Override
    public void run() {}
}

class User {
    void use(Impl impl) {
        impl.run();
    }
}