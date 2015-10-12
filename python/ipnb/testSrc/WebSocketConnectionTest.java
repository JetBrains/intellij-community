import com.intellij.openapi.util.Ref;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbOutOutputCell;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbOutputCell;
import org.jetbrains.plugins.ipnb.protocol.IpnbConnection;
import org.jetbrains.plugins.ipnb.protocol.IpnbConnectionListenerBase;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * * Message Spec
 *   http://ipython.org/ipython-doc/dev/development/messaging.html
 *
 * * Notebook REST API
 *   https://github.com/ipython/ipython/wiki/IPEP-16%3A-Notebook-multi-directory-dashboard-and-URL-mapping
 *
 * @author vlan
 */
public class WebSocketConnectionTest extends TestCase {
  @Override
  protected void setUp() throws Exception {
    //WebSocketImpl.DEBUG = true;
  }

  public void testStartAndShutdownKernel() throws URISyntaxException, IOException, InterruptedException {
    final IpnbConnection connection = new IpnbConnection(getTestServerURI(), new IpnbConnectionListenerBase() {
      @Override
      public void onOpen(@NotNull IpnbConnection connection) {
        assertTrue(connection.getKernelId().length() > 0);
        connection.shutdown();
      }
    });
    connection.close();
  }

  public void testBasicWebSocket() throws IOException, URISyntaxException, InterruptedException {
    final Ref<Boolean> evaluated = Ref.create(false);
    final IpnbConnection connection = new IpnbConnection(getTestServerURI(), new IpnbConnectionListenerBase() {
      private String myMessageId;

      @Override
      public void onOpen(@NotNull IpnbConnection connection) {
        myMessageId = connection.execute("2 + 2");
      }

      @Override
      public void onOutput(@NotNull IpnbConnection connection,
                           @NotNull String parentMessageId) {
        if (myMessageId.equals(parentMessageId)) {
          final ArrayList<IpnbOutputCell> outputs = connection.getOutput();
          assertEquals(1, outputs.size());
          assertEquals(outputs.get(0).getClass(), IpnbOutOutputCell.class);
          final List<String> text = outputs.get(0).getText();
          assertNotNull(text);
          assertEquals("4", text.get(0));
          evaluated.set(true);
          connection.shutdown();
        }
      }
    });
    connection.close();
    assertTrue(evaluated.get());
  }

  public void testCompositeInput() throws IOException, URISyntaxException, InterruptedException {
    final Ref<Boolean> evaluated = Ref.create(false);
    final IpnbConnection connection = new IpnbConnection(getTestServerURI(), new IpnbConnectionListenerBase() {
      private String myMessageId;

      @Override
      public void onOpen(@NotNull IpnbConnection connection) {
        myMessageId = connection.execute("def simple_crit_func(feat_sub):\n" +
                                         "\n" +
                                         "    \"\"\" Returns sum of numerical values of an input list. \"\"\" \n" +
                                         "\n" +
                                         "    return sum(feat_sub)\n" +
                                         "\n" +
                                         "simple_crit_func([1,2,4])");
      }

      @Override
      public void onOutput(@NotNull IpnbConnection connection,
                           @NotNull String parentMessageId) {
        if (myMessageId.equals(parentMessageId)) {
          final ArrayList<IpnbOutputCell> outputs = connection.getOutput();
          assertEquals(1, outputs.size());
          assertEquals(outputs.get(0).getClass(), IpnbOutOutputCell.class);
          final List<String> text = outputs.get(0).getText();
          assertNotNull(text);
          assertEquals("7", text.get(0));
          evaluated.set(true);
          connection.shutdown();
        }
      }
    });
    connection.close();
    assertTrue(evaluated.get());
  }

  @NotNull
  public static String getTestServerURI() {
    return "http://127.0.0.1:8888";
  }
}
