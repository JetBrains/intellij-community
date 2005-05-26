class YoYo {
    void bar () {}
    void f () {
        YoYo yoYoYo = foo();
        <caret>yoYoYo.bar();
    }

    private YoYoYo foo() {

    }
    class YoYoYo extends YoYo {}
}