import java.awt.Window;
import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.List;

class Types {
    public void <caret>method(List<? super JComponent> v) {
        Object o = v.get(0);
    }
    public void context() {
        List<Window> list = new ArrayList<Window>();
        Object o = list.get(0);
    }
}
