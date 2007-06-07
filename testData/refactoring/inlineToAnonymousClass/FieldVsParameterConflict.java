class A {
    public void doTest() {
        Object b = new Inner(new Throwable("t"));
    }

    private class <caret>Inner {
        private Throwable t;
        private String myMessage;

        public Inner(Throwable t) {
            this.t = t;
            String msg = this.t.getMessage();
            myMessage = msg;
        }
    
        public String toString() {
            return "A";
        }
    }
}