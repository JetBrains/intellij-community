public class Scr10884 {
 int foo() {
                return 1;
        }
        Y bar() {
            return new Y(<caret>foo()) {
	    };
        }
}

class Y {
        Y(int x) {}
        int foo() {
                return 2;
        }
}