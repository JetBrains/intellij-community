package org.jetbrains.idea.reposearch;

import com.intellij.openapi.util.Ref;
import com.sun.net.httpserver.HttpServer;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.kpmsearch.PackageSearchEndpointConfig;
import org.jetbrains.idea.kpmsearch.PackageSearchService;
import org.junit.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.net.HttpURLConnection.HTTP_OK;

public class PackageSearchServiceTest {
  private static final String LOCALHOST = "127.0.0.1";
  private HttpServer myServer;
  private String myUrl;

  private String suggestEndPoint = "/suggest";
  private String fulltextEndPoint = "/fulltext";

  private String response = "{\"requestId\":\"Root=1-5fa519a5-3df7e7a3525d29ad730ae091\",\"groupId\":\"org.apache.maven.plugins\",\"artifactId\":\"maven-co\",\"items\":[{\"groupId\":\"org.apache.maven.plugins\",\"artifactId\":\"maven-continuum-plugin\",\"versions\":[\"1.1\",\"1.1-beta-4\",\"1.1-beta-3\",\"1.1-beta-2\",\"1.1-beta-1\"]},{\"groupId\":\"org.apache.maven.plugins\",\"artifactId\":\"maven-core-it-plugin\",\"versions\":[\"1.0\"]},{\"groupId\":\"org.apache.maven.plugins\",\"artifactId\":\"maven-compiler-plugin\",\"versions\":[\"3.8.1\",\"3.8.0\",\"3.7.0\",\"3.6.2\",\"3.6.1\",\"3.6.0\",\"3.5.1\",\"3.5\",\"3.3\",\"3.2\",\"3.1\",\"3.0\",\"2.5.1\",\"2.5\",\"2.4\",\"2.3.2\",\"2.3.1\",\"2.3\",\"2.2\",\"2.1\",\"2.0.2\",\"2.0\",\"2.0-beta-1\",\"2.0.1\"]}]}";


  @Before
  public  void setUp() throws IOException {
    myServer = HttpServer.create();
    myServer.bind(new InetSocketAddress(LOCALHOST, 0), 1);
    myServer.start();
    myUrl = "http://" + LOCALHOST + ":" + myServer.getAddress().getPort();
  }

  @After
  public void tearDown() {
    myServer.stop(0);
  }

  @Test
  public void testSuggestTextSearch() {
    Ref<Map<String, String>> params = new Ref<>();


    myServer.createContext(suggestEndPoint, ex -> {
      try {
        params.set(getQueryMap(ex.getRequestURI()));
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(HTTP_OK, 0);
        ex.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
      } finally {
        ex.close();
      }

    });
    List<RepositoryArtifactData> data = new ArrayList<>();

    new PackageSearchService(new MyPackageSearchEndpointConfig()).suggestPrefix("org.apache.maven.plugins", "maven-co", data::add);

    TestCase.assertNotNull(params.get());
    TestCase.assertEquals(3, data.size());
    TestCase.assertEquals("org.apache.maven.plugins", params.get().get("groupId") );
    TestCase.assertEquals("maven-co", params.get().get("artifactId"));
  }

  @Test
  public void testSuggestTextSearchWithStuff() {
    Ref<Map<String, String>> params = new Ref<>();


    myServer.createContext(suggestEndPoint, ex -> {
      try {
        params.set(getQueryMap(ex.getRequestURI()));
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(HTTP_OK, 0);
        ex.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
      } finally {
        ex.close();
      }

    });
    List<RepositoryArtifactData> data = new ArrayList<>();

    new PackageSearchService(new MyPackageSearchEndpointConfig()).suggestPrefix("!@#org.apache.$%^$@$%^maven.plugins", "!@$maven@!#$@#$-co", data::add);

    TestCase.assertNotNull(params.get());
    TestCase.assertEquals(3, data.size());
    TestCase.assertEquals("org.apache.maven.plugins", params.get().get("groupId") );
    TestCase.assertEquals("maven-co", params.get().get("artifactId"));
  }

  @Test
  public void testFullTextSearch() {
    Ref<Map<String, String>> params = new Ref<>();


    myServer.createContext(fulltextEndPoint, ex -> {
      try {
        params.set(getQueryMap(ex.getRequestURI()));
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(HTTP_OK, 0);
        ex.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
      } finally {
        ex.close();
      }

    });
    List<RepositoryArtifactData> data = new ArrayList<>();

    new PackageSearchService(new MyPackageSearchEndpointConfig()).fulltextSearch("blablabla", data::add);

    TestCase.assertNotNull(params.get());
    TestCase.assertEquals(3, data.size());
    TestCase.assertEquals("blablabla", params.get().get("query") );
  }

  private static Map<String, String> getQueryMap(URI uri) {
    String[] params = uri.getQuery().split("&");
    Map<String, String> map = new HashMap<>();

    for (String param : params) {
      String[] split = param.split("=");
      map.put(split[0], split[1]);
    }
    return map;
  }

  private class MyPackageSearchEndpointConfig implements PackageSearchEndpointConfig {
    @Override
    public @Nullable String getFullTextUrl() {
      return myUrl + fulltextEndPoint;
    }

    @Override
    public @Nullable String getSuggestUrl() {
      return myUrl + suggestEndPoint;
    }

    @Override
    public @NotNull String getUserAgent() {
      return "TEST";
    }

    @Override
    public int getReadTimeout() {
      return 1000;
    }

    @Override
    public int getConnectTimeout() {
      return 1000;
    }

    @Override
    public boolean forceHttps() {
      return false;
    }
  }
}
