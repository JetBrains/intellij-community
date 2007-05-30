import java.awt.event.*;

class A {
    private Object b = new Inner();

    public class <caret>Inner implements Runnable, ActionListener {
        public void run() {
        }

        public void actionPerformed(ActionEvent e) {
        }
    }
}