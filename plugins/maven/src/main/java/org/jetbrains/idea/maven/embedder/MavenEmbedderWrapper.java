package org.jetbrains.idea.maven.embedder;

import com.intellij.openapi.progress.ProcessCanceledException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.embedder.MavenEmbedderException;
import org.apache.maven.embedder.MavenEmbedderHelper;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.extension.ExtensionScanningException;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.reactor.MavenExecutionException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.project.MavenProcessCanceledException;
import org.jetbrains.idea.maven.project.TransferListenerAdapter;
import org.jetbrains.idea.maven.runner.logger.MavenEmbeddedLogger;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MavenEmbedderWrapper {
  private MavenEmbedder myEmbedder;

  public MavenEmbedderWrapper(MavenEmbedder embedder) {
    myEmbedder = embedder;
    installTransferListener();
  }

  private void installTransferListener() {
    myEmbedder.getDefaultRequest().setTransferListener(new TransferListenerAdapter());

    try {
      WagonManager wagon = (WagonManager)myEmbedder.getPlexusContainer().lookup(WagonManager.ROLE);
      wagon.setDownloadMonitor(new TransferListenerAdapter());
    }
    catch (ComponentLookupException e) {
      MavenLog.LOG.info(e);
    }
  }

  public MavenExecutionResult readProjectWithDependencies(MavenExecutionRequest request) throws MavenProcessCanceledException {
    try {
      request.setTransferListener(new TransferListenerAdapter());
      return myEmbedder.readProjectWithDependencies(request);
    }
    catch (ProcessCanceledException e) {
      throw new MavenProcessCanceledException();
    }
  }

  public Set<MavenId> retrieveAndResetUnresolvedArtifactIds() {
    try {
      WagonManager wagon = (WagonManager)myEmbedder.getPlexusContainer().lookup(WagonManager.ROLE);

      CustomWagonManager customWagon = (CustomWagonManager)wagon;
      Set<MavenId> result = customWagon.getUnresolvedIds();
      customWagon.resetUnresolvedArtifacts();

      return result;
    }
    catch (ComponentLookupException e) {
      MavenLog.LOG.info(e);
      return Collections.emptySet();
    }
  }

  public MavenExecutionResult execute(MavenExecutionRequest request) throws MavenProcessCanceledException {
    try {
      request.setTransferListener(new TransferListenerAdapter());
      return myEmbedder.execute(request);
    }
    catch (ProcessCanceledException e) {
      throw new MavenProcessCanceledException();
    }
  }

  public Model readModel(String path) throws MavenProcessCanceledException, IOException, XmlPullParserException {
    try {
      return myEmbedder.readModel(new File(path));
    }
    catch (ProcessCanceledException e) {
      throw new MavenProcessCanceledException();
    }
  }

  public MavenProject readProject(String path) throws MavenProcessCanceledException,
                                                      ExtensionScanningException,
                                                      MavenExecutionException,
                                                      ProjectBuildingException {
    try {
      return myEmbedder.readProject(new File(path));
    }
    catch (ProcessCanceledException e) {
      throw new MavenProcessCanceledException();
    }
  }

  public void resolve(Artifact artifact, List remoteRepositories) throws MavenProcessCanceledException {
    try {
      myEmbedder.resolve(artifact, remoteRepositories, myEmbedder.getLocalRepository());
    }
    catch (ArtifactResolutionException e) {
    }
    catch (ArtifactNotFoundException e) {
    }
    catch (ProcessCanceledException e) {
      throw new MavenProcessCanceledException();
    }
    catch (Exception e) {
    }
  }

  public boolean resolvePlugin(Plugin plugin, MavenProject project) throws MavenProcessCanceledException {
    try {
      MavenEmbedderHelper.verifyPlugin(plugin, project, myEmbedder);
    }
    catch (ProcessCanceledException e) {
      throw new MavenProcessCanceledException();
    }
    catch (Exception e) {
      return false;
    }
    return true;
  }

  public Artifact createArtifact(String groupId, String artifactId, String version, String type, String classifier) {
    return myEmbedder.createArtifactWithClassifier(groupId,
                                                   artifactId,
                                                   version,
                                                   type,
                                                   classifier);
  }

  public Artifact createProjectArtifact(String groupId, String artifactId, String version) {
    try {
      ArtifactFactory factory = (ArtifactFactory)myEmbedder.getPlexusContainer().lookup(ArtifactFactory.ROLE);
      return factory.createProjectArtifact(groupId, artifactId, version);
    }
    catch (ComponentLookupException e) {
      throw new RuntimeException(e);
    }
  }

  public void setLogger(MavenEmbeddedLogger logger) {
    myEmbedder.setLogger(logger);
  }

  public String getLocalRepository() {
    return myEmbedder.getLocalRepository().getBasedir();
  }

  public MavenEmbedder getEmbedder() {
    return myEmbedder;
  }

  public void release() {
    try {
      myEmbedder.stop();
    }
    catch (MavenEmbedderException e) {
      MavenLog.LOG.info(e);
    }
  }
}
