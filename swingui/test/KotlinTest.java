import org.hanuna.gitalk.controller.Controller;
import org.hanuna.gitalk.graph.GraphModel;
import org.hanuna.gitalk.graph.builder.ReaderGraphModel;
import org.hanuna.gitalk.swingui.GitAlkUI;

import java.io.IOException;

/**
 * @author erokhins
 */
public class KotlinTest {

    public static void main(String[] args) throws IOException {
        GraphModel graphModel = ReaderGraphModel.read();
        Controller controller = new Controller(graphModel);
        controller.prepare();
        GitAlkUI ui = new GitAlkUI(controller);
        ui.showUi();
    }
}
