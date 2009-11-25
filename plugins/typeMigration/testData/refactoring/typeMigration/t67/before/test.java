class Test {
    private Object myForAccess;
    private Object forAccess() {
        return myForAccess;
    }
    public void methMemAcc(Object p) {
        p = this.forAccess();
    }
}