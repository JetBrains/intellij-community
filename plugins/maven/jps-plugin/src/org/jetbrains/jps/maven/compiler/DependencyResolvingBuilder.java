/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.maven.compiler;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.idea.maven.aether.ProgressConsumer;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.maven.model.RepositoryLibraryDescriptor;
import org.jetbrains.jps.maven.model.impl.JpsMavenRepositoryLibraryType;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsLibraryDependency;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;
import org.jetbrains.jps.model.serialization.JpsPathVariablesConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Eugene Zhuravlev
 *         Date: 21-Jun-16
 */
public class DependencyResolvingBuilder extends ModuleLevelBuilder{
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.maven.compiler.DependencyResolvingBuilder");
  private static final String NAME = "Maven Dependency Resolver";
  private static final String MAVEN_REPOSITORY_PATH_VAR = "MAVEN_REPOSITORY";
  private static final String DEFAULT_MAVEN_REPOSITORY_PATH = ".m2/repository";

  private static final Key<ArtifactRepositoryManager> MANAGER_KEY = Key.create("_artifact_repository_manager_");
  private static final Key<Exception> RESOLVE_ERROR_KEY = Key.create("_artifact_repository_resolve_error_");

  public DependencyResolvingBuilder() {
    super(BuilderCategory.INITIAL);
  }

  public List<String> getCompilableFileExtensions() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return NAME;
  }

  public void buildStarted(CompileContext context) {
    ResourceGuard.init(context);
  }

  @Override
  public void chunkBuildStarted(CompileContext context, ModuleChunk chunk) {
    try {
      checkDependencies(context, chunk);
    }
    catch (Exception e) {
      context.putUserData(RESOLVE_ERROR_KEY, e);
    }
  }

  @Override
  public ExitCode build(CompileContext context,
                        ModuleChunk chunk,
                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                        OutputConsumer outputConsumer) throws ProjectBuildException, IOException {

    final Exception error = context.getUserData(RESOLVE_ERROR_KEY);
    if (error != null) {
      final StringBuilder builder = new StringBuilder().append("Error resolving dependencies for ").append(chunk.getPresentableShortName());
      final String details = error.getMessage();
      if (details != null) {
        builder.append(": ").append(details);
      }
      final String msg = builder.toString();
      LOG.info(msg, error);
      context.processMessage(new CompilerMessage(NAME, BuildMessage.Kind.ERROR, msg));
      return ExitCode.ABORT;
    }

    return ExitCode.OK;
  }

  private static void checkDependencies(CompileContext context, ModuleChunk chunk) throws Exception {
    final Collection<JpsTypedLibrary<JpsSimpleElement<RepositoryLibraryDescriptor>>> libs = getRepositoryLibraries(chunk);
    if (!libs.isEmpty()) {
      final ArtifactRepositoryManager repoManager = getRepositoryManager(context);
      for (JpsTypedLibrary<JpsSimpleElement<RepositoryLibraryDescriptor>> lib : libs) {
        final RepositoryLibraryDescriptor descriptor = lib.getProperties().getData();
        final ResourceGuard guard = ResourceGuard.get(context, descriptor);
        if (guard.requestProcessing(context.getCancelStatus())) {
          try {
            final Collection<File> resolved = repoManager.resolveDependency(descriptor.getGroupId(), descriptor.getArtifactId(), descriptor.getVersion());
            syncPaths(resolved, lib);
          }
          finally {
            guard.finish();
          }
        }
      }
    }
  }

  private static void syncPaths(@NotNull Collection<File> resolved, @NotNull JpsTypedLibrary<JpsSimpleElement<RepositoryLibraryDescriptor>> lib) throws Exception {
    if (resolved.isEmpty()) {
      LOG.info("No artifacts were resolved for repository dependency " + lib.getProperties().getData().getMavenId());
      return;
    }
    final THashSet<File> libFiles = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
    libFiles.addAll(lib.getFiles(JpsOrderRootType.COMPILED));
    libFiles.removeAll(resolved);

    if (!libFiles.isEmpty()) {
      Map<String, File> nameToArtifactMap = null;
      for (File file : libFiles) {
        if (file.exists()) {
          continue;
        }

        if (nameToArtifactMap == null) { // lazy init
          nameToArtifactMap = new THashMap<String, File>(FileUtil.PATH_HASHING_STRATEGY);
          for (File f : resolved) {
            final File prev = nameToArtifactMap.put(f.getName(), f);
            if (prev != null) {
              throw new Exception("Ambiguous artifacts with the same name: " + prev.getPath() + " and " + f.getPath());
            }
          }
        }

        final File resolvedArtifact = nameToArtifactMap.get(file.getName());
        if (resolvedArtifact != null) {
          FileUtil.copy(resolvedArtifact, file);
        }
      }
    }
  }

  private static final class ResourceGuard {
    private static final Key<ConcurrentMap<RepositoryLibraryDescriptor, ResourceGuard>> CONTEXT_KEY = GlobalContextKey.create("_artifact_repository_resolved_libraries_");
    private static final byte INITIAL = 0;
    private static final byte PROGRESS = 1;
    private static final byte FINISHED = 2;
    private byte myState = INITIAL;

    synchronized boolean requestProcessing(final CanceledStatus cancelStatus) {
      if (myState == INITIAL) {
        myState = PROGRESS;
        return true;
      }
      while (myState == PROGRESS) {
        if (cancelStatus.isCanceled()) {
          break;
        }
        try {
          this.wait(1000L);
        }
        catch (InterruptedException ignored) {
        }
      }
      return false;
    }

    synchronized void finish() {
      myState = FINISHED;
      this.notifyAll();
    }

    static void init(CompileContext context) {
      context.putUserData(CONTEXT_KEY, ContainerUtil.<RepositoryLibraryDescriptor, ResourceGuard>newConcurrentMap());
    }

    @NotNull
    static ResourceGuard get(CompileContext context, RepositoryLibraryDescriptor descriptor) {
      final ConcurrentMap<RepositoryLibraryDescriptor, ResourceGuard> map = context.getUserData(CONTEXT_KEY);
      assert map != null;
      final ResourceGuard g = new ResourceGuard();
      final ResourceGuard existing = map.putIfAbsent(descriptor, g);
      return existing != null? existing : g;
    }
  }

  @NotNull
  private static Collection<JpsTypedLibrary<JpsSimpleElement<RepositoryLibraryDescriptor>>> getRepositoryLibraries(ModuleChunk chunk) {
    final Collection<JpsTypedLibrary<JpsSimpleElement<RepositoryLibraryDescriptor>>> result = new SmartHashSet<JpsTypedLibrary<JpsSimpleElement<RepositoryLibraryDescriptor>>>();
    for (JpsModule module : chunk.getModules()) {
      for (JpsDependencyElement dep : module.getDependenciesList().getDependencies()) {
        if (dep instanceof JpsLibraryDependency) {
          final JpsLibrary _lib = ((JpsLibraryDependency)dep).getLibrary();
          final JpsTypedLibrary<JpsSimpleElement<RepositoryLibraryDescriptor>> lib = _lib != null ? _lib.asTyped(JpsMavenRepositoryLibraryType.INSTANCE) : null;
          if (lib != null) {
            result.add(lib);
          }
        }
      }
    }
    return result;
  }

  private static ArtifactRepositoryManager getRepositoryManager(final CompileContext context) {
    ArtifactRepositoryManager manager = MANAGER_KEY.get(context);
    if (manager == null) {
      manager = new ArtifactRepositoryManager(getLocalRepoDir(context), new ProgressConsumer() {
        public void consume(String message) {
          context.processMessage(new ProgressMessage(message));
        }
      });
      // further init manager here
      MANAGER_KEY.set(context, manager);
    }
    return manager;
  }

  @NotNull
  private static File getLocalRepoDir(CompileContext context) {
    final JpsPathVariablesConfiguration pvConfig = JpsModelSerializationDataService.getPathVariablesConfiguration(context.getProjectDescriptor().getModel().getGlobal());
    final String localRepoPath = pvConfig != null? pvConfig.getUserVariableValue(MAVEN_REPOSITORY_PATH_VAR) : null;
    if (localRepoPath != null) {
      return new File(localRepoPath);
    }
    final String root = System.getProperty("user.home", null);
    return root != null ? new File(root, DEFAULT_MAVEN_REPOSITORY_PATH) : new File(DEFAULT_MAVEN_REPOSITORY_PATH);
  }

}
