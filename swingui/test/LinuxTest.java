import org.hanuna.gitalk.controller.UI_Controller;
import org.hanuna.gitalk.refs.RefsModel;
import org.hanuna.gitalk.swingui.GitAlkUI;

import java.io.IOException;

/**
 * @author erokhins
 */
public class LinuxTest {
    public static void main(String[] args) throws IOException {
        Controller controller = new Controller();
        controller.prepare();
        UI_Controller UIController = new UI_Controller(controller.getGraph(), controller.getRefsModel());
        GitAlkUI ui = new GitAlkUI(UIController);
        ui.showUi();
    }
}
