import javax.swing.JButton;
import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.List;

class Types {
    public void <caret>method(List<? extends JComponent> v) {
        int i = v.get(0).getX();
    }
    public void context() {
        List<JButton> list = new ArrayList<JButton>();
        int j = list.get(0).getX();
    }
}
