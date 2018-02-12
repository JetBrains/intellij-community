package org.jetbrains.plugins.ipnb;

import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbOutOutputCell;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbOutputCell;
import org.jetbrains.plugins.ipnb.protocol.IpnbConnection;
import org.jetbrains.plugins.ipnb.protocol.IpnbConnectionListenerBase;
import org.junit.Assume;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.List;

import static org.jetbrains.plugins.ipnb.run.IpnbCommandLineState.getHostPortFromUrl;

/**
 * * Message Spec
 * http://ipython.org/ipython-doc/dev/development/messaging.html
 * <p>
 * * Notebook REST API
 * https://github.com/ipython/ipython/wiki/IPEP-16%3A-Notebook-multi-directory-dashboard-and-URL-mapping
 *
 * @author vlan
 */
public class WebSocketConnectionTest extends TestCase {
  @Override
  protected void setUp() {
    //WebSocketImpl.DEBUG = true;
    Assume.assumeTrue(pingHost(getTestServerURI()));
  }

  public void testStartAndShutdownKernel() throws URISyntaxException, IOException, InterruptedException {
    final IpnbConnection connection = new IpnbConnection(getTestServerURI(), new IpnbConnectionListenerBase() {
      @Override
      public void onOpen(@NotNull IpnbConnection connection) {
        assertTrue(connection.getKernelId().length() > 0);
        connection.shutdown();
      }
    }, null, DefaultProjectFactory.getInstance().getDefaultProject(), "");
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
          final IpnbOutputCell output = connection.getOutput();
          assertEquals(output.getClass(), IpnbOutOutputCell.class);
          final List<String> text = output.getText();
          assertNotNull(text);
          assertEquals("4", text.get(0));
          evaluated.set(true);
          connection.shutdown();
        }
      }
    }, null, DefaultProjectFactory.getInstance().getDefaultProject(), "");
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
          final IpnbOutputCell output = connection.getOutput();
          assertEquals(output.getClass(), IpnbOutOutputCell.class);
          final List<String> text = output.getText();
          assertNotNull(text);
          assertEquals("7", text.get(0));
          evaluated.set(true);
          connection.shutdown();
        }
      }
    }, null, DefaultProjectFactory.getInstance().getDefaultProject(), "");
    connection.close();
    assertTrue(evaluated.get());
  }

  @NotNull
  public static String getTestServerURI() {
    return "http://127.0.0.1:8888";
  }

  public static boolean pingHost(@NotNull final String url) {
    final Pair<String, String> hostPort = getHostPortFromUrl(url);
    if (hostPort == null) return false;
    final String host = hostPort.getFirst();
    final String port = hostPort.getSecond();
    try (Socket socket = new Socket()) {
      if (port == null) {
        return InetAddress.getByName(host).isReachable(1000);
      }
      socket.connect(new InetSocketAddress(host, Integer.parseInt(port)), 1000);
      return true;
    }
    catch (IOException | IllegalArgumentException e) {
      return false;
    }
  }
}
