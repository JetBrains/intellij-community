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

import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
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
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.interpolation.ModelInterpolator;
import org.apache.maven.project.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.server.embedder.CustomMaven3ModelInterpolator2;
import org.jetbrains.idea.maven.server.embedder.MavenExecutionResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.rmi.RemoteException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author Vladislav.Soroka
 * @since 1/20/2015
 */
public abstract class Maven3ServerEmbedder extends MavenRemoteObject implements MavenServerEmbedder {

  public final static boolean USE_MVN2_COMPATIBLE_DEPENDENCY_RESOLVING = System.getProperty("idea.maven3.use.compat.resolver") != null;
  private final static String MAVEN_VERSION = System.getProperty(MAVEN_EMBEDDER_VERSION);
  private static final Pattern PROPERTY_PATTERN = Pattern.compile("-D(\\S+?)(?:=(.+))?");
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
                                                @NotNull List<MavenRemoteRepository> remoteRepositories)
    throws RemoteException {
    try {
      Artifact artifact =
        new DefaultArtifact(groupId, artifactId, "", Artifact.SCOPE_COMPILE, "pom", null, new DefaultArtifactHandler("pom"));
      List<ArtifactVersion> versions = getComponent(ArtifactMetadataSource.class)
        .retrieveAvailableVersions(
          artifact,
          getLocalRepository(),
          convertRepositories(remoteRepositories));
      return ContainerUtil.map(versions, new Function<ArtifactVersion, String>() {
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
    List<ProjectBuildingResult> buildingResults = new SmartList<ProjectBuildingResult>();

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

    public MyProjectBuildingResult(String projectId,
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
  public MavenModel readModel(File file) throws RemoteException {
    return null;
  }

  public static Map<String, String> getMavenAndJvmConfigProperties(File workingDir) {
    if (workingDir == null) {
      return Collections.emptyMap();
    }
    File baseDir = MavenServerUtil.findMavenBasedir(workingDir);

    Map<String, String> result = new HashMap<String, String>();
    readConfigFile(baseDir, File.separator + ".mvn" + File.separator + "jvm.config", result);
    readConfigFile(baseDir, File.separator + ".mvn" + File.separator + "maven.config", result);
    return result.isEmpty() ? Collections.<String, String>emptyMap() : result;
  }

  private static void readConfigFile(File baseDir, String relativePath, Map<String, String> result) {
    File configFile = new File(baseDir, relativePath);

    if (configFile.exists() && configFile.isFile()) {
      try {
        InputStream in = new FileInputStream(configFile);
        try {
          for (String parameter : ParametersListUtil.parse(StreamUtil.readText(in, CharsetToolkit.UTF8))) {
            Matcher matcher = PROPERTY_PATTERN.matcher(parameter);
            if (matcher.matches()) {
              result.put(matcher.group(1), StringUtil.notNullize(matcher.group(2), ""));
            }
          }
        }
        finally {
          in.close();
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

  @SuppressWarnings({"unchecked"})
  public abstract <T> T getComponent(Class<T> clazz, String roleHint);

  @SuppressWarnings({"unchecked"})
  public abstract <T> T getComponent(Class<T> clazz);

  public abstract void executeWithMavenSession(MavenExecutionRequest request, Runnable runnable);

  public abstract MavenExecutionRequest createRequest(File file,
                                                      List<String> activeProfiles,
                                                      List<String> inactiveProfiles,
                                                      List<String> goals)
    throws RemoteException;
}
