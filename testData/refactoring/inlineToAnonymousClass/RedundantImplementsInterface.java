import java.awt.event.*;

class A {
    private ActionListener b = new Inner();

    private abstract class MyActionListener implements ActionListener {
    }

    private class <caret>Inner extends MyActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
        }
    }
}