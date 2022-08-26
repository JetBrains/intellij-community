class AccessorsArePreserved {
    private Integer someInt = 1;

    // get header
    public Integer getSomeInt() {
        // get body 1
        System.out.println("Some text");
        // get body 2
        return someInt;
    } // get footer

    // set header
    public void setSomeInt(Integer state) {
        // set body 1
        someInt = state;
        // set body 2
        System.out.println("Some text");
    } // set footer
}

class AccessorsAreRemoved {
    private Integer someInt = 1;

    // get header
    public Integer getSomeInt() {
        // get body
        return someInt;
    } // get footer

    // set header
    public void setSomeInt(Integer state) {
        // set body
        someInt = state;
    } // set footer
}