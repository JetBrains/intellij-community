import org.hanuna.gitalk.controller.Controller;
import org.hanuna.gitalk.controller.DataPack;
import org.hanuna.gitalk.controller.git_log.GitException;
import org.hanuna.gitalk.ui_controller.UI_Controller;
import org.hanuna.gitalk.swing_ui.GitAlkUI;

import java.io.IOException;

/**
 * @author erokhins
 */
public class KotlinTest {

    public static void main(String[] args) throws IOException, GitException {
        Controller controller = new Controller();
        DataPack dataPack = controller.prepare();
        UI_Controller UIController = new UI_Controller(dataPack);
        GitAlkUI ui = new GitAlkUI(UIController);
        ui.showUi();
    }
}
