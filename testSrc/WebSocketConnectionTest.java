import com.google.gson.Gson;
import junit.framework.TestCase;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * @author vlan
 */
public class WebSocketConnectionTest extends TestCase {
  private static final String LOCATION = "127.0.0.1:8888";
  private static final String API_URL = "/api";
  private static final String KERNELS_URL = API_URL + "/kernels";

  public void testGetKernels() throws URISyntaxException, IOException {
    final Kernel kernel = getFirstKernel("http://" + LOCATION + KERNELS_URL);
    assertNotNull(kernel);
    assertTrue(kernel.getId().length() > 0);
  }

  public void testBasicWebSocket() throws IOException, URISyntaxException, InterruptedException {
    final Kernel kernel = getFirstKernel("http://" + LOCATION + KERNELS_URL);
    assertNotNull(kernel);
    final URI shellURI = new URI("ws://" + LOCATION + KERNELS_URL + "/" + kernel.getId() + "/shell");
    final WebSocketClient shellClient = new WebSocketClient(shellURI, new Draft_17()) {
      @Override
      public void onOpen(@NotNull ServerHandshake handshakeData) {
        System.out.format("onOpen(%s)\n", handshakeData);
      }

      @Override
      public void onMessage(@NotNull String message) {
        System.out.format("onMessage(%s)\n", message);
      }

      @Override
      public void onClose(int code, @NotNull String reason, boolean remote) {
        System.out.format("onClose(%d, %s, %b)\n", code, reason, remote);
      }

      @Override
      public void onError(@NotNull Exception e) {
        System.out.format("onError(%s)\n", e);
      }
    };
    final Thread thread = new Thread(shellClient);
    thread.start();
    try {
      thread.join();
    }
    finally {
      shellClient.close();
    }
  }

  @Nullable
  private Kernel getFirstKernel(@NotNull String url) throws IOException {
    final String s = readURL(url);
    final Gson gson = new Gson();
    final Kernel[] kernels = gson.fromJson(s, Kernel[].class);
    return kernels.length > 0 ? kernels[0] : null;
  }

  @NotNull
  private static String readURL(@NotNull String url) throws IOException {
    final BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(url).openStream(), "utf-8"));
    final StringBuilder builder = new StringBuilder();
    char[] buffer = new char[4096];
    int n;
    while ((n = reader.read(buffer)) != -1) {
      builder.append(buffer, 0, n);
    }
    return builder.toString();
  }

  @SuppressWarnings("UnusedDeclaration")
  private static class Kernel {
    @NotNull private String id;

    @NotNull
    public String getId() {
      return id;
    }
  }
}
