class QualifiedSuper extends Base {
}

class Base {


    class Inner {
        void goo() {
            Base.this.toString();
        }
    }
}
