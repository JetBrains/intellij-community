class Inference {
        public <T> T getX() {
            return null;
        }

        void foo (String s) {}

        {
           String v2 = new Inference().getX();
           foo(<caret>v2);
        }
}