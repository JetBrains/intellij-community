import org.hanuna.gitalk.controller.Controller;
import org.hanuna.gitalk.ui_controller.UI_Controller;
import org.hanuna.gitalk.swing_ui.GitAlkUI;

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
