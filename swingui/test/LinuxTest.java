import org.hanuna.gitalk.controller.Controller;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.builder.ReaderGraphModel;
import org.hanuna.gitalk.swingui.GitAlkUI;

import java.io.IOException;

/**
 * @author erokhins
 */
public class LinuxTest {
    public static void main(String[] args) throws IOException {
        Graph graph = ReaderGraphModel.read();
        Controller controller = new Controller(graph);
        controller.prepare();
        GitAlkUI ui = new GitAlkUI(controller);
        ui.showUi();
    }
}
