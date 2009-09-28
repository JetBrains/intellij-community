package org.jetbrains.idea.maven.indices;

import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.WagonException;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.repository.Repository;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.sonatype.nexus.index.updater.ResourceFetcher;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class MavenIndexFetcher implements ResourceFetcher {
  private final String myOriginalRepositoryId;
  private final String myOriginalRepositoryUrl;
  private final WagonManager myWagonManager;
  private final TransferListener myListener;
  private Wagon myWagon = null;

  public MavenIndexFetcher(String originalRepositoryId, String originalRepositoryUrl, WagonManager wagonManager, TransferListener listener) {
    myOriginalRepositoryId = originalRepositoryId;
    myOriginalRepositoryUrl = originalRepositoryUrl;
    myWagonManager = wagonManager;
    myListener = listener;
  }

  public void connect(String _ignoredContextId, String _ignoredUrl) throws IOException {
    String mirrorUrl = myWagonManager.getMirrorRepository(new DefaultArtifactRepository(myOriginalRepositoryId,
                                                                                        myOriginalRepositoryUrl,
                                                                                        null)).getUrl();
    String indexUrl = mirrorUrl + (mirrorUrl.endsWith("/") ? "" : "/") + ".index";
    Repository repository = new Repository(myOriginalRepositoryId, indexUrl);

    try {
      myWagon = myWagonManager.getWagon(repository);
      myWagon.addTransferListener(myListener);

      myWagon.connect(repository,
                      myWagonManager.getAuthenticationInfo(repository.getId()),
                      myWagonManager.getProxy(repository.getProtocol()));
    }
    catch (AuthenticationException e) {
      IOException newEx = new IOException("Authentication exception connecting to " + repository);
      newEx.initCause(e);
      throw newEx;
    }
    catch (WagonException e) {
      IOException newEx = new IOException("Wagon exception connecting to " + repository);
      newEx.initCause(e);
      throw newEx;
    }
  }

  public void disconnect() {
    if (myWagon == null) return;

    try {
      myWagon.disconnect();
    }
    catch (ConnectionException ex) {
      MavenLog.LOG.warn(ex);
    }
  }

  public void retrieve(String name, File targetFile) throws IOException {
    try {
      myWagon.get(name, targetFile);
    }
    catch (AuthorizationException e) {
      IOException newEx = new IOException("Authorization exception retrieving " + name);
      newEx.initCause(e);
      throw newEx;
    }
    catch (ResourceDoesNotExistException e) {
      IOException newEx = new FileNotFoundException("Resource " + name + " does not exist");
      newEx.initCause(e);
      throw newEx;
    }
    catch (WagonException e) {
      IOException newEx = new IOException("Transfer for " + name + " failed");
      newEx.initCause(e);
      throw newEx;
    }
  }
}
