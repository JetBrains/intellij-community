/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.text.VersionComparatorUtil;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.DefaultMaven;
import org.apache.maven.Maven;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.ResolutionListener;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.interpolation.ModelInterpolator;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.project.*;
import org.apache.maven.session.scope.internal.SessionScope;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.RepositorySystemSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.server.embedder.CustomMaven3ModelInterpolator2;
import org.jetbrains.idea.maven.server.embedder.MavenExecutionResult;
import org.jetbrains.idea.maven.server.security.MavenToken;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author Vladislav.Soroka
 */
public abstract class Maven3ServerEmbedder extends MavenRemoteObject implements MavenServerEmbedder {

  public interface RunnableThrownRemote {
    void run() throws RemoteException;
  }

  public final static boolean USE_MVN2_COMPATIBLE_DEPENDENCY_RESOLVING = System.getProperty("idea.maven3.use.compat.resolver") != null;
  private final static String MAVEN_VERSION = System.getProperty(MAVEN_EMBEDDER_VERSION);
  private static final Pattern PROPERTY_PATTERN = Pattern.compile("\"-D([\\S&&[^=]]+)(?:=([^\"]+))?\"|-D([\\S&&[^=]]+)(?:=(\\S+))?");
  protected final MavenServerSettings myServerSettings;

  protected Maven3ServerEmbedder(MavenServerSettings settings) {
    myServerSettings = settings;
    initLog4J(myServerSettings);
  }

  private static void initLog4J(MavenServerSettings settings) {
    try {
      BasicConfigurator.configure();
      final Level rootLoggerLevel = toLog4JLevel(settings.getLoggingLevel());
      Logger.getRootLogger().setLevel(rootLoggerLevel);
      if (!rootLoggerLevel.isGreaterOrEqual(Level.ERROR)) {
        Logger.getLogger("org.apache.maven.wagon.providers.http.httpclient.wire").setLevel(Level.ERROR);
        Logger.getLogger("org.apache.http.wire").setLevel(Level.ERROR);
      }
    }
    catch (Throwable ignore) {
    }
  }

  private static Level toLog4JLevel(int level) {
    switch (level) {
      case MavenServerConsole.LEVEL_DEBUG:
        return Level.ALL;
      case MavenServerConsole.LEVEL_ERROR:
        return Level.ERROR;
      case MavenServerConsole.LEVEL_FATAL:
        return Level.FATAL;
      case MavenServerConsole.LEVEL_DISABLED:
        return Level.OFF;
      case MavenServerConsole.LEVEL_INFO:
        return Level.INFO;
      case MavenServerConsole.LEVEL_WARN:
        return Level.WARN;
    }
    return Level.INFO;
  }

  protected abstract ArtifactRepository getLocalRepository();

  @NotNull
  @Override
  public List<String> retrieveAvailableVersions(@NotNull String groupId,
                                                @NotNull String artifactId,
                                                @NotNull List<MavenRemoteRepository> remoteRepositories, MavenToken token)
    throws RemoteException {
    MavenServerUtil.checkToken(token);
    try {
      Artifact artifact =
        new DefaultArtifact(groupId, artifactId, "", Artifact.SCOPE_COMPILE, "pom", null, new DefaultArtifactHandler("pom"));
      List<ArtifactVersion> versions = getComponent(ArtifactMetadataSource.class)
        .retrieveAvailableVersions(
          artifact,
          getLocalRepository(),
          convertRepositories(remoteRepositories));
      return ContainerUtilRt.map2List(versions, new Function<ArtifactVersion, String>() {
        @Override
        public String fun(ArtifactVersion version) {
          return version.toString();
        }
      });
    }
    catch (Exception e) {
      Maven3ServerGlobals.getLogger().info(e);
    }
    return Collections.emptyList();
  }

  @NotNull
  protected List<ProjectBuildingResult> getProjectBuildingResults(@NotNull MavenExecutionRequest request, @NotNull Collection<File> files) {
    final ProjectBuilder builder = getComponent(ProjectBuilder.class);

    CustomMaven3ModelInterpolator2 modelInterpolator = (CustomMaven3ModelInterpolator2)getComponent(ModelInterpolator.class);

    String savedLocalRepository = modelInterpolator.getLocalRepository();
    modelInterpolator.setLocalRepository(request.getLocalRepositoryPath().getAbsolutePath());
    List<ProjectBuildingResult> buildingResults = new ArrayList<ProjectBuildingResult>();

    final ProjectBuildingRequest projectBuildingRequest = request.getProjectBuildingRequest();
    projectBuildingRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
    projectBuildingRequest.setResolveDependencies(false);

    try {
      if (files.size() == 1) {
        buildSinglePom(builder, buildingResults, projectBuildingRequest, files.iterator().next());
      }
      else {
        try {
          buildingResults = builder.build(new ArrayList<File>(files), false, projectBuildingRequest);
        }
        catch (ProjectBuildingException e) {
          for (ProjectBuildingResult result : e.getResults()) {
            if (result.getProject() != null) {
              buildingResults.add(result);
            }
            else {
              buildSinglePom(builder, buildingResults, projectBuildingRequest, result.getPomFile());
            }
          }
        }
      }
    }
    finally {
      modelInterpolator.setLocalRepository(savedLocalRepository);
    }
    return buildingResults;
  }

  private void buildSinglePom(ProjectBuilder builder,
                              List<ProjectBuildingResult> buildingResults,
                              ProjectBuildingRequest projectBuildingRequest,
                              File pomFile) {
    try {
      ProjectBuildingResult build = builder.build(pomFile, projectBuildingRequest);
      buildingResults.add(build);
    }
    catch (ProjectBuildingException e) {
      handleProjectBuildingException(buildingResults, e);
    }
  }

  protected void handleProjectBuildingException(List<ProjectBuildingResult> buildingResults, ProjectBuildingException e) {
    List<ProjectBuildingResult> results = e.getResults();
    if (results != null && !results.isEmpty()) {
      buildingResults.addAll(results);
    }
    else {
      Throwable cause = e.getCause();
      List<ModelProblem> problems = null;
      if (cause instanceof ModelBuildingException) {
        problems = ((ModelBuildingException)cause).getProblems();
      }
      buildingResults.add(new MyProjectBuildingResult(null, e.getPomFile(), null, problems, null));
    }
  }

  private static class MyProjectBuildingResult implements ProjectBuildingResult {

    private final String myProjectId;
    private final File myPomFile;
    private final MavenProject myMavenProject;
    private final List<ModelProblem> myProblems;
    private final DependencyResolutionResult myDependencyResolutionResult;

    MyProjectBuildingResult(String projectId,
                            File pomFile,
                            MavenProject mavenProject,
                            List<ModelProblem> problems,
                            DependencyResolutionResult dependencyResolutionResult) {
      myProjectId = projectId;
      myPomFile = pomFile;
      myMavenProject = mavenProject;
      myProblems = problems;
      myDependencyResolutionResult = dependencyResolutionResult;
    }

    @Override
    public String getProjectId() {
      return myProjectId;
    }

    @Override
    public File getPomFile() {
      return myPomFile;
    }

    @Override
    public MavenProject getProject() {
      return myMavenProject;
    }

    @Override
    public List<ModelProblem> getProblems() {
      return myProblems;
    }

    @Override
    public DependencyResolutionResult getDependencyResolutionResult() {
      return myDependencyResolutionResult;
    }
  }

  protected void addMvn2CompatResults(MavenProject project,
                                      List<Exception> exceptions,
                                      List<ResolutionListener> listeners,
                                      ArtifactRepository localRepository,
                                      Collection<MavenExecutionResult> executionResults) {
    ArtifactResolutionRequest resolutionRequest = new ArtifactResolutionRequest();
    resolutionRequest.setArtifactDependencies(project.getDependencyArtifacts());
    resolutionRequest.setArtifact(project.getArtifact());
    resolutionRequest.setManagedVersionMap(project.getManagedVersionMap());
    resolutionRequest.setLocalRepository(localRepository);
    resolutionRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());
    resolutionRequest.setListeners(listeners);

    resolutionRequest.setResolveRoot(false);
    resolutionRequest.setResolveTransitively(true);

    ArtifactResolver resolver = getComponent(ArtifactResolver.class);
    ArtifactResolutionResult result = resolver.resolve(resolutionRequest);

    project.setArtifacts(result.getArtifacts());
    executionResults.add(new MavenExecutionResult(project, exceptions));
  }

  @Override
  @Nullable
  public MavenModel readModel(File file, MavenToken token) throws RemoteException {
    MavenServerUtil.checkToken(token);
    return null;
  }

  public static Map<String, String> getMavenAndJvmConfigProperties(File workingDir) {
    if (workingDir == null) {
      return Collections.emptyMap();
    }
    File baseDir = MavenServerUtil.findMavenBasedir(workingDir);

    Map<String, String> result = new HashMap<String, String>();
    readConfigFiles(baseDir, result);
    return result.isEmpty() ? Collections.<String, String>emptyMap() : result;
  }

  static void readConfigFiles(File baseDir, Map<String, String> result) {
    readConfigFile(baseDir, File.separator + ".mvn" + File.separator + "jvm.config", result, "");
    readConfigFile(baseDir, File.separator + ".mvn" + File.separator + "maven.config", result, "true");
  }

  private static void readConfigFile(File baseDir, String relativePath, Map<String, String> result, String valueIfMissing) {
    File configFile = new File(baseDir, relativePath);

    if (configFile.exists() && configFile.isFile()) {
      try {
        String text = FileUtilRt.loadFile(configFile, "UTF-8");
        Matcher matcher = PROPERTY_PATTERN.matcher(text);
        while (matcher.find()) {
          if (matcher.group(1) != null) {
            result.put(matcher.group(1), StringUtilRt.notNullize(matcher.group(2), valueIfMissing));
          }
          else {
            result.put(matcher.group(3), StringUtilRt.notNullize(matcher.group(4), valueIfMissing));
          }
        }
      }
      catch (IOException ignore) {
      }
    }
  }

  @NotNull
  protected abstract List<ArtifactRepository> convertRepositories(List<MavenRemoteRepository> repositories) throws RemoteException;

  @Nullable
  public String getMavenVersion() {
    return MAVEN_VERSION;
  }

  public abstract <T> T getComponent(Class<T> clazz, String roleHint);

  public abstract <T> T getComponent(Class<T> clazz);

  protected void executeWithMavenSession(MavenExecutionRequest request, final Runnable runnable) throws RemoteException {
    executeWithMavenSession(request, new RunnableThrownRemote() {
      @Override
      public void run() throws RemoteException {
        runnable.run();
      }
    });
  }

  protected void executeWithMavenSession(MavenExecutionRequest request, RunnableThrownRemote runnable) throws RemoteException {

    if (VersionComparatorUtil.compare(getMavenVersion(), "3.2.5") >= 0) {
      executeWithSessionScope(request, runnable);
    }
    else {
      executeWithMavenSessionLegacy(request, runnable);
    }
  }

  protected void executeWithMavenSessionLegacy(MavenExecutionRequest request, final Runnable runnable) throws RemoteException {
    executeWithMavenSessionLegacy(request, new RunnableThrownRemote() {
      @Override
      public void run() throws RemoteException {
        runnable.run();
      }
    });
  }


  protected void executeWithMavenSessionLegacy(MavenExecutionRequest request, RunnableThrownRemote runnable) throws RemoteException {
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

  @NotNull
  private MavenSession createMavenSession(MavenExecutionRequest request, DefaultMaven maven) {
    RepositorySystemSession repositorySession = maven.newRepositorySession(request);
    request.getProjectBuildingRequest().setRepositorySession(repositorySession);
    return new MavenSession(getContainer(), repositorySession, request, new DefaultMavenExecutionResult());
  }

  private void notifyAfterSessionStart(MavenSession mavenSession) {
    try {
      for (AbstractMavenLifecycleParticipant listener : getLifecycleParticipants(Collections.<MavenProject>emptyList())) {
        listener.afterSessionStart(mavenSession);
      }
    }
    catch (MavenExecutionException e) {
      throw new RuntimeException(e);
    }
  }


  protected void executeWithSessionScope(MavenExecutionRequest request, RunnableThrownRemote runnable) throws RemoteException {
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

  @NotNull
  protected abstract PlexusContainer getContainer();

  public abstract MavenExecutionRequest createRequest(File file,
                                                      List<String> activeProfiles,
                                                      List<String> inactiveProfiles,
                                                      List<String> goals)
    throws RemoteException;

  protected static void warn(String message, Throwable e) {
    try {
      Maven3ServerGlobals.getLogger().warn(new RuntimeException(message, e));
    }
    catch (RemoteException e1) {
      throw new RuntimeException(e1);
    }
  }

  /**
   * adapted from {@link DefaultMaven#getLifecycleParticipants(Collection)}
   */
  private Collection<AbstractMavenLifecycleParticipant> getLifecycleParticipants(Collection<MavenProject> projects) {
    Collection<AbstractMavenLifecycleParticipant> lifecycleListeners = new LinkedHashSet<AbstractMavenLifecycleParticipant>();

    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      try {
        lifecycleListeners.addAll(getContainer().lookupList(AbstractMavenLifecycleParticipant.class));
      }
      catch (ComponentLookupException e) {
        // this is just silly, lookupList should return an empty list!
        warn("Failed to lookup lifecycle participants", e);
      }

      Collection<ClassLoader> scannedRealms = new HashSet<ClassLoader>();

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
