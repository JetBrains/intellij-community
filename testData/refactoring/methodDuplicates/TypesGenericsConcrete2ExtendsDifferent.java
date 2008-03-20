import javax.swing.JWindow;
import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.List;

class Types {
    public void <caret>method(List<? extends JComponent> v) {
        Object o = v.get(0);
    }
    public void context() {
        List<JWindow> list = new ArrayList<JWindow>();
        Object o = v.get(0);
    }
}
