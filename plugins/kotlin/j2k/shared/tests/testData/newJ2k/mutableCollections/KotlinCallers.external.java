class JavaCaller {
    int read(A a) {
        return a.readInJava().size() + a.fieldReadInJava.size();
    }
}
