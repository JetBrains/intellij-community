package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ArrayUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.proxy.CommonProxy;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;


final class IcsGitConnector {
  private static final int TIMEOUT = 10000;
  private final Repository repository;

  public IcsGitConnector() throws IOException {
    File gitDir = new File(PathManager.getSystemPath(), "ideaConfigurationServer/data");
    FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
    repositoryBuilder.setGitDir(new File(gitDir, Constants.DOT_GIT));
    repository = repositoryBuilder.build();
    if (!gitDir.exists()) {
      repository.create();
    }

    // todo sync with remote
  }

  public static void send(File file, IdeaServerUrlBuilder builder) throws IOException {
    doPost(builder, "upload", file, ContentProcessor.EMPTY);
  }

  @Nullable
  public InputStream loadUserPreferences(@NotNull String path) throws IOException {
    TreeWalk treeWalk = new TreeWalk(repository);
    treeWalk.addTree(new RevWalk(repository).parseCommit(repository.resolve(Constants.HEAD)).getTree());
    treeWalk.setRecursive(true);
    treeWalk.setFilter(PathFilter.create(path));
    if (!treeWalk.next()) {
      return null;
    }

    ObjectId objectId = treeWalk.getObjectId(0);
    ObjectLoader loader = repository.open(objectId);
    return loader.openStream();
  }

  public static void loadAllFiles(IdeaServerUrlBuilder builder, final ContentProcessor processor) throws IOException {
    doPost(builder, "loadAll", null, processor);
  }

  public interface ContentProcessor {
    void processStream(InputStream line) throws IOException;

    ContentProcessor EMPTY = new ContentProcessor() {
      @Override
      public void processStream(final InputStream line) {

      }
    };
  }

  private static void doPost(final IdeaServerUrlBuilder builder, final String actionName, File input, ContentProcessor processor)
    throws IOException {
    doPostImpl(builder, actionName, input, processor);
  }

  private static void doPostImpl(final IdeaServerUrlBuilder builder, final String actionName, File file, ContentProcessor processor)
    throws IOException {
    try {
      final Pair<HttpClient, HttpConnection> pair = createConnection(builder);

      try {
        final PostMethod postMethod = new PostMethod(builder.getServerUrl() + "/" + actionName);

        postMethod.setQueryString(builder.getQueryString());


        final BufferedInputStream content = file != null ? new BufferedInputStream(new FileInputStream(file)) : null;
        try {
          if (content != null) {
            postMethod.setRequestEntity(new InputStreamRequestEntity(content, file.length()));
          }

      /*an attempt to pass proxy credentials to Apache HttpClient API used for connection
      -> with no success since Apache only pass credentials if secure connection protocol is used (a bug)
       did not work before common proxy, so keep as is for now*/
          postMethod.execute(pair.getFirst().getState(), pair.getSecond());

          int code = postMethod.getStatusCode();
          if (code != HttpStatus.SC_OK) {

            String reason = postMethod.getResponseBodyAsString().trim();
            if (HttpStatus.SC_UNAUTHORIZED == code) {
              builder.setUnauthorizedStatus();
              return;
            }

            else if (HttpStatus.SC_SERVICE_UNAVAILABLE == code) {
              builder.setDisconnectedStatus();
              return;
            }

            else {
              throw new IOException(reason);
            }
          }

          InputStream in = postMethod.getResponseBodyAsStream();
          try {
            processor.processStream(in);
          }
          finally {
            in.close();
          }
        }
        finally {
          if (content != null) {
            content.close();
          }
        }
      }
      finally {
        pair.getSecond().close();
      }
    }
    catch (ConnectException e) {
      builder.setDisconnectedStatus();
    }
  }

  public static void delete(IdeaServerUrlBuilder builder) throws IOException {

    doPost(builder, "delete", null, ContentProcessor.EMPTY);
  }

  private static Pair<HttpClient, HttpConnection> createConnection(final IdeaServerUrlBuilder builder) throws IOException {
    //final SimpleHttpConnectionManager manager = new SimpleHttpConnectionManager();
    HttpClient httpClient = new HttpClient();

    httpClient.setTimeout(TIMEOUT);
    httpClient.setConnectionTimeout(TIMEOUT);

    HostConfiguration hostConfiguration = httpClient.getHostConfiguration();

    URI uri = new URI(builder.getServerUrl(), false);

    HttpConfigurable proxySettings = IdeaConfigurationServerManager.getInstance().getIdeaServerSettings().getHttpProxySettings();
    //proxySettings.prepareURL(uri.toString());
    CommonProxy.getInstance().ensureAuthenticator();
//    CommonProxy.getInstance().setCustomAuth(proxySettings);

    hostConfiguration.setHost(uri);

    if (proxySettings.USE_HTTP_PROXY) {
      hostConfiguration.setProxy(proxySettings.PROXY_HOST, proxySettings.PROXY_PORT);
      if (proxySettings.PROXY_AUTHENTICATION) {
        httpClient.getState().setProxyCredentials(AuthScope.ANY, new UsernamePasswordCredentials(proxySettings.PROXY_LOGIN,
                                                                                                 proxySettings.getPlainProxyPassword()));
      }
    }

    HttpConnection connection = httpClient.getHttpConnectionManager().getConnection(hostConfiguration);
    connection.getParams().setConnectionTimeout(TIMEOUT);
    if (!connection.isOpen()) {
      connection.open();
    }
    return new Pair<HttpClient, HttpConnection>(httpClient, connection);
  }

  public static String[] listSubFileNames(IdeaServerUrlBuilder builder) throws IOException {
    final List<String> result = new ArrayList<String>();
    doPost(builder, "list", null, new ContentProcessor() {
      @Override
      public void processStream(final InputStream stream) throws IOException {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.length() > 0) {
            result.add(line);
          }
        }
      }
    });
    return ArrayUtil.toStringArray(result);
  }

  public static String ping(final IdeaServerUrlBuilder builder) throws IOException {
    final Pair<HttpClient, HttpConnection> pair = createConnection(builder);

    try {
      final PostMethod postMethod = new PostMethod(builder.getServerUrl() + "/" + "ping");

      postMethod.setQueryString(builder.getPingQueryString());

      /*an attempt to pass proxy credentials to Apache HttpClient API used for connection
      -> with no success since Apache only pass credentials if secure connection protocol is used (a bug)
       did not work before common proxy, so keep as is for now*/
      postMethod.execute(pair.getFirst().getState(), pair.getSecond());

      if (postMethod.getStatusCode() != HttpStatus.SC_OK) {
        throw new IOException(postMethod.getResponseBodyAsString());
      }
      else {
        return postMethod.getResponseBodyAsString().trim();
      }
    }
    finally {
      pair.getSecond().close();
    }
  }
}
