class A {
    public void usage() {
        int array[150];
        for (int i = 0; i < array.length; i++) {
            method(array[i]);
        }
    }
    public void <caret>method(int i) {
        System.out.println(i);
    }
}