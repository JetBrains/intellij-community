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

import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.execution.MavenExecutionRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;

import java.io.File;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.List;


/**
 * @author Vladislav.Soroka
 * @since 1/20/2015
 */
public abstract class Maven3ServerEmbedder extends MavenRemoteObject implements MavenServerEmbedder {

  public final static boolean USE_MVN2_COMPATIBLE_DEPENDENCY_RESOLVING = System.getProperty("idea.maven3.use.compat.resolver") != null;
  private final static String MAVEN_VERSION = System.getProperty(MAVEN_EMBEDDER_VERSION);

  protected Maven3ServerEmbedder(MavenServerSettings settings) {
    initLog4J(settings);
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
