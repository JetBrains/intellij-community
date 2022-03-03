public class A {
    private String mItem;

    public String getItem() {
        return mItem;
    }

    public void setItem(String mItem) {
        this.mItem = mItem;
    }

    public void f1(String item) {
        mItem = getItem() + item;
    }

    public void f2() {
        String item = getItem();
        setItem(item);
    }
}