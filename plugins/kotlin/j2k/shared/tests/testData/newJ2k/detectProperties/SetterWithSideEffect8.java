public class Foo {
    private Integer someInt;

    public Integer getState() {
        return someInt;
    }

    public void setState(Integer state) {
        someInt = state;
        if (state == 1) System.out.println("1");
    }
}