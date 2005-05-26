class YoYo {
    private YoYoYo myYoYoYo;

    void <caret>foo () {
        myYoYoYo.getClass();
    }
}

class YoYoYo extends YoYo {}