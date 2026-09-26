public class Foo {
    public void m() {
        new Inner().x();
    }

    private static class Inner {
        private void x() {
        }
    }
}