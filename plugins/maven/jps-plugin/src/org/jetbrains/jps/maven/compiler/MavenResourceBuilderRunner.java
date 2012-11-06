/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetRegistry;
import org.jetbrains.jps.builders.ChunkBuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.maven.model.impl.MavenResourcesTarget;
import org.jetbrains.jps.maven.model.impl.MavenResourcesTargetType;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/30/12
 */
public class MavenResourceBuilderRunner extends ModuleLevelBuilder{
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.maven.compiler.MavenResourceBuilderRunner");
  private static final Key<List<MavenResourcesTarget>> PRODUCTION_TARGETS_KEY = Key.create("_maven_production_targets");
  private static final Key<List<MavenResourcesTarget>> TEST_TARGETS_KEY = Key.create("_maven_test_targets");
  private static final Key<MavenResourcesBuilder> BUILDER_KEY = Key.create("_maven_resources_builder_");

  public MavenResourceBuilderRunner() {
    super(BuilderCategory.INITIAL);
  }

  @Override
  public void buildStarted(CompileContext context) {
    BuildTargetRegistry targetRegistry = context.getProjectDescriptor().getBuildTargetIndex();
    PRODUCTION_TARGETS_KEY.set(context, targetRegistry.getAllTargets(MavenResourcesTargetType.PRODUCTION));
    TEST_TARGETS_KEY.set(context, targetRegistry.getAllTargets(MavenResourcesTargetType.TEST));
    for (TargetBuilder<?, ?> builder : BuilderRegistry.getInstance().getTargetBuilders()) {
      if (builder instanceof MavenResourcesBuilder) {
        BUILDER_KEY.set(context, (MavenResourcesBuilder)builder);
        break;
      }
    }

  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "MavenResourceBuilder Runner";
  }

  @Override
  public ExitCode build(CompileContext context,
                        ModuleChunk chunk,
                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                        ChunkBuildOutputConsumer outputConsumer) throws ProjectBuildException {
    ExitCode rc = ExitCode.NOTHING_DONE;
    final MavenResourcesBuilder mavenBuilder = BUILDER_KEY.get(context);
    if (mavenBuilder == null) {
      return rc;
    }
    final Set<ModuleBuildTarget> chunkTargets = chunk.getTargets();
    if (chunkTargets.isEmpty()) {
      return rc;
    }
    final Set<JpsModule> productionModules = new HashSet<JpsModule>();
    final Set<JpsModule> testModules = new HashSet<JpsModule>();
    for (ModuleBuildTarget target : chunkTargets) {
      (target.isTests()? testModules : productionModules).add(target.getModule());
    }
    try {
      if (runMavenBuilderForModules(context, mavenBuilder, productionModules, PRODUCTION_TARGETS_KEY.get(context))) {
        rc = ExitCode.OK;
      }
      if (runMavenBuilderForModules(context, mavenBuilder, testModules, TEST_TARGETS_KEY.get(context))) {
        rc = ExitCode.OK;
      }
      return rc;
    }
    catch (IOException e) {
      throw new ProjectBuildException(e);
    }
  }

  private static boolean runMavenBuilderForModules(CompileContext context,
                                                   MavenResourcesBuilder mavenBuilder,
                                                   Set<JpsModule> modules,
                                                   @Nullable List<MavenResourcesTarget> allTargets)
    throws ProjectBuildException, IOException {
    if (allTargets == null || modules.isEmpty()) {
      return false;
    }
    boolean doneSomething = false;
    final Set<BuildTarget<?>> processed = new HashSet<BuildTarget<?>>();
    for (MavenResourcesTarget mavenTarget : allTargets) {
      if (modules.contains(mavenTarget.getModule())) {
        BuildOperations.ensureFSStateInitialized(context, mavenTarget);
        BuildOperations.buildTarget(mavenTarget, context, mavenBuilder);
        processed.add(mavenTarget);
        doneSomething = true;
      }
    }
    if (!processed.isEmpty()) {
      BuildOperations.markTargetsUpToDate(context, new BuildTargetChunk(processed));
    }
    return doneSomething;
  }

}
