package org.jetbrains.idea.maven.aether;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionScheme;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: 20-Jun-16
 *
 * Aether-based repository manager and dependency resolver using maven implementation of this functionality.
 *
 * instance of this component should be managed by the code which requires dependency resolution functionality
 * all necessary params like path to local repo should be passed in constructor
 *
 */
public class ArtifactRepositoryManager {
  private static final String ARTIFACT_EXTENSION = "jar";
  private final VersionScheme myVersioning = new GenericVersionScheme();
  private final DefaultRepositorySystemSession mySession;

  // todo: more remotes? make remote repos configurable?
  public static final List<RemoteRepository> REMOTE_REPOSITORIES = Collections.singletonList(
    new RemoteRepository.Builder("central", "default", "http://central.maven.org/maven2/").build()
  );
  private static final RepositorySystem ourSystem;
  static {
    final DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
    locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
    locator.addService(TransporterFactory.class, FileTransporterFactory.class);
    locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
    locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
      public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
        if (exception != null) {
          throw new RuntimeException(exception);
        }
      }
    });
    ourSystem = locator.getService(RepositorySystem.class);
  }

  public ArtifactRepositoryManager(@NotNull File localRepositoryPath) {
    this(localRepositoryPath, ProgressConsumer.DEAF);
  }

  public ArtifactRepositoryManager(@NotNull File localRepositoryPath, @NotNull final ProgressConsumer progressConsumer) {
    final DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
    if (progressConsumer != ProgressConsumer.DEAF) {
      session.setTransferListener((TransferListener)Proxy
        .newProxyInstance(session.getClass().getClassLoader(), new Class[]{TransferListener.class}, new InvocationHandler() {
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
          final Object event = args[0];
          if (event instanceof TransferEvent) {
            progressConsumer.consume(event.toString());
            //if (((TransferEvent)event).getType() != TransferEvent.EventType.PROGRESSED) {
            //  progressConsumer.consume(event.toString());
            //}
          }
          return null;
        }
      }));
    }
    // setup session here

    session.setLocalRepositoryManager(ourSystem.newLocalRepositoryManager(session, new LocalRepository(localRepositoryPath)));
    session.setReadOnly();
    mySession = session;
  }

  public Collection<File> resolveDependency(String groupId, String artifactId, String version) throws Exception {
    final DependencyRequest dependencyRequest = new DependencyRequest(
      createCollectRequest(groupId, artifactId, toVersion(version)),
      DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE)
    );

    final DependencyResult result = ourSystem.resolveDependencies(mySession, dependencyRequest);
    final List<File> files = new ArrayList<File>();
    for (ArtifactResult artifactResult : result.getArtifactResults()) {
      files.add(artifactResult.getArtifact().getFile());
    }
    return files;
  }

  private static CollectRequest createCollectRequest(String groupId, String artifactId, Version version) {
    return createCollectRequest(groupId, artifactId, Collections.singleton(version));
  }
  private static CollectRequest createCollectRequest(String groupId, String artifactId, Collection<Version> versions) {
    CollectRequest request = new CollectRequest();
    for (Artifact artifact : toArtifacts(groupId, artifactId, versions)) {
      request.addDependency(new Dependency(artifact, JavaScopes.COMPILE));
    }
    return request.setRepositories(REMOTE_REPOSITORIES);
  }

  private Version toVersion(String version) throws InvalidVersionSpecificationException {
    return myVersioning.parseVersion(version);
  }

  private static List<Artifact> toArtifacts(String groupId, String artifactId, Collection<Version> versions) {
    if (versions.isEmpty()) {
      return Collections.emptyList();
    }
    final List<Artifact> result = new ArrayList<Artifact>(versions.size());
    for (Version version : versions) {
      result.add(new DefaultArtifact(groupId, artifactId, ARTIFACT_EXTENSION, version.toString()));
    }
    return result;
  }

}
