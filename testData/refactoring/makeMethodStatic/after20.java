import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

final class Bug
   extends JFrame {
    public Bug() {
        foo(this);
    }

    public static void foo(Bug anObject) {
        anObject.addWindowListener(anObject.new MyWindowListener());
    }

    private class MyWindowListener
       extends WindowAdapter {
        public void windowActivated(WindowEvent e) {
        }
    }
}