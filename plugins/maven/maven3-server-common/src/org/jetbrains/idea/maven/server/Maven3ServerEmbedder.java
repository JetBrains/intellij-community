// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.util.text.VersionComparatorUtil;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.DefaultMaven;
import org.apache.maven.Maven;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.archetype.catalog.Archetype;
import org.apache.maven.archetype.catalog.ArchetypeCatalog;
import org.apache.maven.archetype.common.ArchetypeArtifactManager;
import org.apache.maven.archetype.exception.UnknownArchetype;
import org.apache.maven.archetype.metadata.ArchetypeDescriptor;
import org.apache.maven.archetype.metadata.RequiredProperty;
import org.apache.maven.archetype.source.ArchetypeDataSource;
import org.apache.maven.archetype.source.ArchetypeDataSourceException;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectUtils;
import org.apache.maven.session.scope.internal.SessionScope;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.RepositorySystemSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.server.security.MavenToken;

import java.io.File;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.apache.maven.archetype.source.CatalogArchetypeDataSource.ARCHETYPE_CATALOG_PROPERTY;
import static org.apache.maven.archetype.source.RemoteCatalogArchetypeDataSource.REPOSITORY_PROPERTY;
import static org.jetbrains.idea.maven.server.Maven3ModelConverter.convertRemoteRepositories;

/**
 * @author Vladislav.Soroka
 */
public abstract class Maven3ServerEmbedder extends MavenServerEmbeddedBase {
  public static final boolean USE_MVN2_COMPATIBLE_DEPENDENCY_RESOLVING = System.getProperty("idea.maven3.use.compat.resolver") != null;
  private static final String MAVEN_VERSION = System.getProperty(MAVEN_EMBEDDER_VERSION);
  protected final MavenServerSettings myServerSettings;

  protected Maven3ServerEmbedder(MavenServerSettings settings) {
    myServerSettings = settings;
    initLogging(myServerSettings);
  }

  private static void initLogging(MavenServerSettings settings) {
    try {
      final Level rootLoggerLevel = toJavaUtilLoggingLevel(settings.getLoggingLevel());
      Logger.getLogger("").setLevel(rootLoggerLevel);
      if (rootLoggerLevel.intValue() < Level.SEVERE.intValue()) {
        Logger.getLogger("org.apache.maven.wagon.providers.http.httpclient.wire").setLevel(Level.SEVERE);
        Logger.getLogger("org.apache.http.wire").setLevel(Level.SEVERE);
      }
    }
    catch (Throwable ignore) {
    }
  }

  private static Level toJavaUtilLoggingLevel(int level) {
    switch (level) {
      case MavenServerConsoleIndicator.LEVEL_DEBUG:
        return Level.ALL;
      case MavenServerConsoleIndicator.LEVEL_ERROR:
        return Level.SEVERE;
      case MavenServerConsoleIndicator.LEVEL_FATAL:
        return Level.SEVERE;
      case MavenServerConsoleIndicator.LEVEL_DISABLED:
        return Level.OFF;
      case MavenServerConsoleIndicator.LEVEL_INFO:
        return Level.INFO;
      case MavenServerConsoleIndicator.LEVEL_WARN:
        return Level.WARNING;
    }
    return Level.INFO;
  }

  protected abstract ArtifactRepository getLocalRepository();

  @Override
  public @Nullable MavenModel readModel(File file, MavenToken token) throws RemoteException {
    MavenServerUtil.checkToken(token);
    return null;
  }

  protected abstract @NotNull List<ArtifactRepository> convertRepositories(List<MavenRemoteRepository> repositories) throws RemoteException;

  protected @NotNull List<ArtifactRepository> map2ArtifactRepositories(List<MavenRemoteRepository> repositories) {
    PlexusContainer container = getContainer();
    List<ArtifactRepository> result = new ArrayList<>();
    for (MavenRemoteRepository each : repositories) {
      try {
        ArtifactRepositoryFactory factory = getComponent(ArtifactRepositoryFactory.class);
        result.add(ProjectUtils.buildArtifactRepository(Maven3ModelConverter.toNativeRepository(each), factory, container));
      }
      catch (InvalidRepositoryException e) {
        MavenServerGlobals.getLogger().warn(e);
      }
    }
    return result;
  }

  public @Nullable String getMavenVersion() {
    return MAVEN_VERSION;
  }

  public abstract <T> T getComponent(Class<T> clazz, String roleHint);

  public abstract <T> T getComponent(Class<T> clazz);

  public void executeWithMavenSession(MavenExecutionRequest request, final Runnable runnable) {
    if (VersionComparatorUtil.compare(getMavenVersion(), "3.2.5") >= 0) {
      executeWithSessionScope(request, runnable);
    }
    else {
      executeWithMavenSessionLegacy(request, runnable);
    }
  }

  protected void executeWithMavenSessionLegacy(MavenExecutionRequest request, Runnable runnable) {
    DefaultMaven maven = (DefaultMaven)getComponent(Maven.class);
    MavenSession mavenSession = createMavenSession(request, maven);
    LegacySupport legacySupport = getComponent(LegacySupport.class);
    MavenSession oldSession = legacySupport.getSession();
    legacySupport.setSession(mavenSession);
    // adapted from {@link DefaultMaven#doExecute(MavenExecutionRequest)}
    notifyAfterSessionStart(mavenSession);
    try {
      runnable.run();
    }
    finally {
      legacySupport.setSession(oldSession);
    }
  }

  private @NotNull MavenSession createMavenSession(MavenExecutionRequest request, DefaultMaven maven) {
    RepositorySystemSession repositorySession = maven.newRepositorySession(request);
    request.getProjectBuildingRequest().setRepositorySession(repositorySession);
    return new MavenSession(getContainer(), repositorySession, request, new DefaultMavenExecutionResult());
  }

  private void notifyAfterSessionStart(MavenSession mavenSession) {
    try {
      for (AbstractMavenLifecycleParticipant listener : getLifecycleParticipants(Collections.emptyList())) {
        listener.afterSessionStart(mavenSession);
      }
    }
    catch (MavenExecutionException e) {
      throw new RuntimeException(e);
    }
  }


  protected void executeWithSessionScope(MavenExecutionRequest request, Runnable runnable) {
    DefaultMaven maven = (DefaultMaven)getComponent(Maven.class);
    SessionScope sessionScope = getComponent(SessionScope.class);
    sessionScope.enter();

    try {
      MavenSession mavenSession = createMavenSession(request, maven);
      sessionScope.seed(MavenSession.class, mavenSession);
      LegacySupport legacySupport = getComponent(LegacySupport.class);
      MavenSession oldSession = legacySupport.getSession();
      legacySupport.setSession(mavenSession);

      notifyAfterSessionStart(mavenSession);
      // adapted from {@link DefaultMaven#doExecute(MavenExecutionRequest)}
      try {
        runnable.run();
      }
      finally {
        legacySupport.setSession(oldSession);
      }
    }
    finally {
      sessionScope.exit();
    }
  }

  protected abstract @NotNull PlexusContainer getContainer();

  public MavenExecutionRequest createRequest(File file,
                                             List<String> activeProfiles,
                                             List<String> inactiveProfiles) {
    return createRequest(file, activeProfiles, inactiveProfiles, new Properties());
  }

  public abstract MavenExecutionRequest createRequest(File file,
                                                      List<String> activeProfiles,
                                                      List<String> inactiveProfiles,
                                                      @NotNull Properties customProperties);

  protected static void warn(String message, Throwable e) {
    MavenServerGlobals.getLogger().warn(new RuntimeException(message, e));
  }

  @Override
  public HashSet<MavenRemoteRepository> resolveRepositories(@NotNull ArrayList<MavenRemoteRepository> repositories, MavenToken token)
    throws RemoteException {
    MavenServerUtil.checkToken(token);
    try {
      return new HashSet<>(
        convertRemoteRepositories(convertRepositories(new ArrayList<>(repositories))));
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public ArrayList<MavenArchetype> getLocalArchetypes(MavenToken token, @NotNull String path) throws RemoteException {
    MavenServerUtil.checkToken(token);
    try {
      ArchetypeDataSource source = getComponent(ArchetypeDataSource.class, "catalog");
      Properties properties = new Properties();
      properties.setProperty(ARCHETYPE_CATALOG_PROPERTY, path);
      ArchetypeCatalog archetypeCatalog = source.getArchetypeCatalog(properties);
      return getArchetypes(archetypeCatalog);
    }
    catch (Exception e) {
      MavenServerGlobals.getLogger().warn(e);
    }
    return new ArrayList<>();
  }

  @Override
  public ArrayList<MavenArchetype> getRemoteArchetypes(MavenToken token, @NotNull String url) throws RemoteException {
    MavenServerUtil.checkToken(token);
    try {
      ArchetypeDataSource source = getComponent(ArchetypeDataSource.class, "remote-catalog");
      Properties properties = new Properties();
      properties.setProperty(REPOSITORY_PROPERTY, url);
      ArchetypeCatalog archetypeCatalog = source.getArchetypeCatalog(properties);
      return getArchetypes(archetypeCatalog);
    }
    catch (ArchetypeDataSourceException e) {
      MavenServerGlobals.getLogger().warn(e);
    }
    return new ArrayList<>();
  }

  @Override
  public @Nullable HashMap<String, String> resolveAndGetArchetypeDescriptor(@NotNull String groupId, @NotNull String artifactId,
                                                                            @NotNull String version,
                                                                            @NotNull ArrayList<MavenRemoteRepository> repositories,
                                                                            @Nullable String url, MavenToken token) throws RemoteException {
    MavenServerUtil.checkToken(token);
    try {
      MavenExecutionRequest request = createRequest(null, null, null);
      List<ArtifactRepository> artifactRepositories = map2ArtifactRepositories(repositories);
      for (ArtifactRepository repository : artifactRepositories) {
        request.addRemoteRepository(repository);
      }

      HashMap<String, String> result = new HashMap<>();
      AtomicBoolean unknownArchetypeError = new AtomicBoolean(false);
      executeWithMavenSession(request, () -> {
        MavenArtifactRepository artifactRepository = null;
        if (url != null) {
          artifactRepository = new MavenArtifactRepository();
          artifactRepository.setId("archetype");
          artifactRepository.setUrl(url);
          artifactRepository.setLayout(new DefaultRepositoryLayout());
        }

        List<ArtifactRepository> remoteRepositories = request.getRemoteRepositories();

        ArchetypeArtifactManager archetypeArtifactManager = getComponent(ArchetypeArtifactManager.class);
        ArchetypeDescriptor descriptor = null;
        try {
          descriptor = archetypeArtifactManager.getFileSetArchetypeDescriptor(
            groupId, artifactId, version, artifactRepository,
            getLocalRepository(), remoteRepositories);
        }
        catch (UnknownArchetype e) {
          unknownArchetypeError.set(true);
        }
        if (descriptor != null && descriptor.getRequiredProperties() != null) {
          for (RequiredProperty property : descriptor.getRequiredProperties()) {
            result.put(property.getKey(), property.getDefaultValue() != null ? property.getDefaultValue() : "");
          }
        }
      });
      return unknownArchetypeError.get() ? null : result;
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  private static @NotNull ArrayList<MavenArchetype> getArchetypes(ArchetypeCatalog archetypeCatalog) {
    ArrayList<MavenArchetype> result = new ArrayList<>(archetypeCatalog.getArchetypes().size());
    for (Archetype each : archetypeCatalog.getArchetypes()) {
      result.add(Maven3ModelConverter.convertArchetype(each));
    }
    return result;
  }


  /**
   * adapted from {@link DefaultMaven#getLifecycleParticipants(Collection)}
   */
  private Collection<AbstractMavenLifecycleParticipant> getLifecycleParticipants(Collection<MavenProject> projects) {
    Collection<AbstractMavenLifecycleParticipant> lifecycleListeners = new LinkedHashSet<>();

    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      try {
        lifecycleListeners.addAll(getContainer().lookupList(AbstractMavenLifecycleParticipant.class));
      }
      catch (ComponentLookupException e) {
        // this is just silly, lookupList should return an empty list!
        warn("Failed to lookup lifecycle participants", e);
      }

      Collection<ClassLoader> scannedRealms = new HashSet<>();

      for (MavenProject project : projects) {
        ClassLoader projectRealm = project.getClassRealm();

        if (projectRealm != null && scannedRealms.add(projectRealm)) {
          Thread.currentThread().setContextClassLoader(projectRealm);

          try {
            lifecycleListeners.addAll(getContainer().lookupList(AbstractMavenLifecycleParticipant.class));
          }
          catch (ComponentLookupException e) {
            // this is just silly, lookupList should return an empty list!
            warn("Failed to lookup lifecycle participants", e);
          }
        }
      }
    }
    finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
    }

    return lifecycleListeners;
  }
}
