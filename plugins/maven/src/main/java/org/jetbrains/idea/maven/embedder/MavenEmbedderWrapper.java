package org.jetbrains.idea.maven.embedder;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Ref;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.embedder.MavenEmbedderException;
import org.apache.maven.embedder.MavenEmbedderHelper;
import org.apache.maven.embedder.PlexusLoggerAdapter;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.monitor.event.DefaultEventMonitor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.jetbrains.idea.maven.project.MavenProcess;
import org.jetbrains.idea.maven.project.MavenProcessCanceledException;
import org.jetbrains.idea.maven.project.TransferListenerAdapter;
import org.jetbrains.idea.maven.utils.MavenId;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

  public MavenExecutionResult readProjectWithDependencies(MavenExecutionRequest request,
                                                          MavenProcess p) throws MavenProcessCanceledException {
    return doExecute(request, new RequestExecutor() {
      public MavenExecutionResult execute(MavenExecutionRequest request) {
        return myEmbedder.readProjectWithDependencies(request);
      }
    }, p);
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

  public MavenExecutionResult execute(MavenExecutionRequest request, MavenProcess p) throws MavenProcessCanceledException {
    request.addEventMonitor(new DefaultEventMonitor(new PlexusLoggerAdapter(myEmbedder.getLogger())));
    return doExecute(request, new RequestExecutor() {
      public MavenExecutionResult execute(MavenExecutionRequest request) {
        return myEmbedder.execute(request);
      }
    }, p);
  }

  public Model readModel(final String path, MavenProcess p) throws MavenProcessCanceledException {
    return doExecute(new Executor<Model>() {
      public Model execute() throws Exception {
        return myEmbedder.readModel(new File(path));
      }
    }, p);
  }

  public MavenProject readProject(final String path, MavenProcess p) throws MavenProcessCanceledException {
    return doExecute(new Executor<MavenProject>() {
      public MavenProject execute() throws Exception {
        return myEmbedder.readProject(new File(path));
      }
    }, p);
  }

  public void resolve(Artifact artifact, List<ArtifactRepository> remoteRepositories) throws MavenProcessCanceledException {
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
  //
  //public Set<Artifact> resolveTransitively(MavenProjectModel mavenProject, Artifact artifact) throws MavenProcessCanceledException {
  //  try {
  //    PlexusContainer container = myEmbedder.getPlexusContainer();
  //
  //    ArtifactResolver resolver = (ArtifactResolver)container.lookup(ArtifactResolver.class);
  //    ArtifactMetadataSource metadataSource = (ArtifactMetadataSource)container.lookup(ArtifactMetadataSource.class);
  //
  //    ArtifactResolutionRequest request = new ArtifactResolutionRequest()
  //        .setArtifact(mavenProject.getMavenProject().getArtifact())
  //        .setArtifactDependencies(Collections.singleton(artifact))
  //        .setLocalRepository(myEmbedder.getLocalRepository())
  //        .setRemoteRepostories(mavenProject.getRepositories())
  //        .setManagedVersionMap(mavenProject.getMavenProject().getManagedVersionMap()) // todo can be null
  //        .setMetadataSource(metadataSource);
  //
  //    return resolver.resolve(request).getArtifacts();
  //  }
  //  catch (ComponentLookupException e) {
  //    throw new RuntimeException(e);
  //  }
  //}

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

  private interface Executor<T> {
    T execute() throws Exception;
  }

  private interface RequestExecutor {
    MavenExecutionResult execute(MavenExecutionRequest request) throws Exception;
  }

  private static MavenExecutionResult doExecute(final MavenExecutionRequest request,
                                                final RequestExecutor executor,
                                                MavenProcess p) throws MavenProcessCanceledException {
    return doExecute(new Executor<MavenExecutionResult>() {
      public MavenExecutionResult execute() throws Exception {
        request.setTransferListener(new TransferListenerAdapter());
        return executor.execute(request);
      }
    }, p);
  }

  private static <T> T doExecute(final Executor<T> executor, MavenProcess p) throws MavenProcessCanceledException {
    final Ref<T> result = new Ref<T>();
    final boolean[] cancelled = new boolean[1];
    final Throwable[] exception = new Throwable[1];

    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        try {
          result.set(executor.execute());
        }
        catch (ProcessCanceledException e) {
          cancelled[0] = true;
        }
        catch (Throwable e) {
          exception[0] = e;
        }
      }
    });

    while (true) {
      p.checkCanceled();
      try {
        future.get(50, TimeUnit.MILLISECONDS);
      }
      catch (TimeoutException ignore) {
      }
      catch (ExecutionException e) {
        throw new RuntimeException(e.getCause());
      }
      catch (InterruptedException e) {
        throw new MavenProcessCanceledException();
      }

      if (future.isDone()) break;
    }

    if (cancelled[0]) throw new MavenProcessCanceledException();
    if (exception[0] != null) throw new RuntimeException(exception[0]);

    return result.get();
  }
}
