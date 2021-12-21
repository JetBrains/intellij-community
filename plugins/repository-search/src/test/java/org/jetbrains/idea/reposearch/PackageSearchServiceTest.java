package org.jetbrains.idea.reposearch;

import com.intellij.openapi.util.Ref;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.sun.net.httpserver.HttpServer;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.kpmsearch.PackageSearchEndpointConfig;
import org.jetbrains.idea.kpmsearch.PackageSearchService;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.net.HttpURLConnection.HTTP_OK;

@RunWith(JUnit4.class)
public class PackageSearchServiceTest extends UsefulTestCase {
  private static final String LOCALHOST = "127.0.0.1";
  private HttpServer myServer;
  private String myUrl;

  private static final String suggestEndPoint = "/suggest";
  private static final String fulltextEndPoint = "/fulltext";

  private static final String response =
    "{\"requestId\":\"Root=1-5fa519a5-3df7e7a3525d29ad730ae091\",\"groupId\":\"org.apache.maven.plugins\",\"artifactId\":\"maven-co\",\"items\":[{\"groupId\":\"org.apache.maven.plugins\",\"artifactId\":\"maven-continuum-plugin\",\"versions\":[\"1.1\",\"1.1-beta-4\",\"1.1-beta-3\",\"1.1-beta-2\",\"1.1-beta-1\"]},{\"groupId\":\"org.apache.maven.plugins\",\"artifactId\":\"maven-core-it-plugin\",\"versions\":[\"1.0\"]},{\"groupId\":\"org.apache.maven.plugins\",\"artifactId\":\"maven-compiler-plugin\",\"versions\":[\"3.8.1\",\"3.8.0\",\"3.7.0\",\"3.6.2\",\"3.6.1\",\"3.6.0\",\"3.5.1\",\"3.5\",\"3.3\",\"3.2\",\"3.1\",\"3.0\",\"2.5.1\",\"2.5\",\"2.4\",\"2.3.2\",\"2.3.1\",\"2.3\",\"2.2\",\"2.1\",\"2.0.2\",\"2.0\",\"2.0-beta-1\",\"2.0.1\"]}]}";

  private static final String longResponse =
    "{\"requestId\":\"Root=1-asdasdasd\",\"groupId\":\"test\",\"artifactId\":\"test\",\"items\":[{\"groupId\":\"test1\",\"artifactId\":\"test1\",\"versions\":[\"1.1\",\"1.2\"]},{\"groupId\":\"test2\",\"artifactId\":\"test2\",\"versions\":[\"1.1\",\"1.2\"]},{\"groupId\":\"test3\",\"artifactId\":\"test3\",\"versions\":[\"1.1\",\"1.2\"]},{\"groupId\":\"test4\",\"artifactId\":\"test4\",\"versions\":[\"1.1\",\"1.2\"]},{\"groupId\":\"test5\",\"artifactId\":\"test5\",\"versions\":[\"1.1\",\"1.2\"]},{\"groupId\":\"test6\",\"artifactId\":\"test6\",\"versions\":[\"1.1\",\"1.2\"]},{\"groupId\":\"test7\",\"artifactId\":\"test7\",\"versions\":[\"1.1\",\"1.2\"]},{\"groupId\":\"test8\",\"artifactId\":\"test8\",\"versions\":[\"1.1\",\"1.2\"]}," +
    "{\"groupId\":\"test9\",\"artifactId\":\"test9\",\"versions\":[\"1.1\",\"1.2\"]},{\"groupId\":\"test10\",\"artifactId\":\"test10\",\"versions\":[\"1.1\",\"1.2\"]},{\"groupId\":\"test11\",\"artifactId\":\"test11\",\"versions\":[\"1.1\",\"1.2\"]},{\"groupId\":\"test12\",\"artifactId\":\"test12\",\"versions\":[\"1.1\",\"1.2\"]},{\"groupId\":\"test13\",\"artifactId\":\"test13\",\"versions\":[\"1.1\",\"1.2\"]},{\"groupId\":\"test14\",\"artifactId\":\"test14\",\"versions\":[\"1.1\",\"1.2\"]},{\"groupId\":\"test15\",\"artifactId\":\"test15\",\"versions\":[\"1.1\",\"1.2\"]},{\"groupId\":\"test16\",\"artifactId\":\"test16\",\"versions\":[\"1.1\",\"1.2\"]},{\"groupId\":\"test17\",\"artifactId\":\"test17\",\"versions\":[\"1.1\",\"1.2\"]}," +
    "{\"groupId\":\"test18\",\"artifactId\":\"test18\",\"versions\":[\"1.1\",\"1.2\"]},{\"groupId\":\"test19\",\"artifactId\":\"test19\",\"versions\":[\"1.1\",\"1.2\"]},{\"groupId\":\"test20\",\"artifactId\":\"test20\",\"versions\":[\"1.1\",\"1.2\"]},{\"groupId\":\"test21\",\"artifactId\":\"test21\",\"versions\":[\"1.1\",\"1.2\"]},{\"groupId\":\"test22\",\"artifactId\":\"test22\",\"versions\":[\"1.1\",\"1.2\"]}]}";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myServer = HttpServer.create();
    myServer.bind(new InetSocketAddress(LOCALHOST, 0), 1);
    myServer.start();
    myUrl = "http://" + LOCALHOST + ":" + myServer.getAddress().getPort();
  }

  protected void tearDown() throws Exception {
    try {
      myServer.stop(0);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Test
  public void testSuggestTextSearch() {
    Ref<Map<String, String>> params = createServer(suggestEndPoint, response);
    List<RepositoryArtifactData> data = new ArrayList<>();

    new PackageSearchService(new MyPackageSearchEndpointConfig()).suggestPrefix("org.apache.maven.plugins", "maven-co", data::add);

    TestCase.assertNotNull(params.get());
    TestCase.assertEquals(3, data.size());
    TestCase.assertEquals("org.apache.maven.plugins", params.get().get("groupId"));
    TestCase.assertEquals("maven-co", params.get().get("artifactId"));
  }

  @Test
  public void testSuggestTextSearchWithStuff() {
    Ref<Map<String, String>> params = createServer(suggestEndPoint, response);

    List<RepositoryArtifactData> data = new ArrayList<>();

    new PackageSearchService(new MyPackageSearchEndpointConfig()).suggestPrefix("!@#org.apache.$%^$@$%^maven.plugins", "!@$maven@!#$@#$-co",
                                                                                data::add);

    TestCase.assertNotNull(params.get());
    TestCase.assertEquals(3, data.size());
    TestCase.assertEquals("org.apache.maven.plugins", params.get().get("groupId"));
    TestCase.assertEquals("maven-co", params.get().get("artifactId"));
  }

  @Test
  public void testFullTextSearch() {
    Ref<Map<String, String>> params = createServer(fulltextEndPoint, response);
    List<RepositoryArtifactData> data = new ArrayList<>();

    new PackageSearchService(new MyPackageSearchEndpointConfig()).fulltextSearch("blablabla", data::add);

    TestCase.assertNotNull(params.get());
    TestCase.assertEquals(3, data.size());
    TestCase.assertEquals("blablabla", params.get().get("query"));
  }

  @Test
  public void testLongResponceTextSearch() {
    Ref<Map<String, String>> params = createServer(fulltextEndPoint, longResponse);
    List<RepositoryArtifactData> data = new ArrayList<>();

    new PackageSearchService(new MyPackageSearchEndpointConfig()).fulltextSearch("test", data::add);

    TestCase.assertNotNull(params.get());
    TestCase.assertEquals(22, data.size());
    List<String> expected = IntStream.range(1, 23).mapToObj(i -> "test" + i).collect(Collectors.toList());
    assertOrderedEquals(expected, ContainerUtil.map(data, d -> ((MavenRepositoryArtifactInfo)d).getGroupId()));
    assertOrderedEquals(expected, ContainerUtil.map(data, d -> ((MavenRepositoryArtifactInfo)d).getArtifactId()));
  }

  @NotNull
  private Ref<Map<String, String>> createServer(String endpoint, String serverResponce) {
    Ref<Map<String, String>> params = new Ref<>();

    myServer.createContext(endpoint, ex -> {
      try {
        params.set(getQueryMap(ex.getRequestURI()));
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(HTTP_OK, serverResponce.length());
        ex.getResponseBody().write(serverResponce.getBytes(StandardCharsets.UTF_8));
      }
      finally {
        ex.close();
      }
    });
    return params;
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
