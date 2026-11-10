import java.util.*;

class A {
    void changedInAnonymousClass(List<String> items) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                items.add("a");
            }
        };
        runnable.run();
    }

    void readInAnonymousClass(List<String> items) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                System.out.println(items.size());
            }
        };
        runnable.run();
    }

    void changedInLocalClass(List<String> items) {
        class Adder {
            void add() {
                items.add("a");
            }
        }
        new Adder().add();
    }
}
