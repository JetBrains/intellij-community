import java.awt.event.*;

class A {
    private ActionListener b = new Inner();

    private class <caret>Inner implements ActionListener {
        public void actionPerformed(ActionEvent e) {
        }
    }
}