// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.java.facet;

import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectWizardStepFactory;
import com.intellij.ide.util.projectWizard.importSources.DetectedContentRoot;
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.ProjectFromSourcesBuilder;
import com.intellij.ide.util.projectWizard.importSources.ProjectStructureDetector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.WebModuleTypeBase;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtilRt;
import com.jetbrains.python.PythonModuleTypeBase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


final class PyProjectStructureDetector extends ProjectStructureDetector {
  private static final Logger LOG = Logger.getInstance(PyProjectStructureDetector.class);
  public static final @NlsSafe String PYTHON = "Python";


  @NotNull
  @Override
  public DirectoryProcessingResult detectRoots(@NotNull File dir,
                                               File @NotNull [] children,
                                               @NotNull File base,
                                               @NotNull List<DetectedProjectRoot> result) {
    LOG.info("Detecting roots under " + dir);
    for (File child : children) {
      final String name = child.getName();
      if (FileUtilRt.extensionEquals(name, "py")) {
        LOG.info("Found Python file " + child.getPath());
        result.add(new DetectedContentRoot(dir, PYTHON, PythonModuleTypeBase.getInstance(), WebModuleTypeBase.getInstance()));
        return DirectoryProcessingResult.SKIP_CHILDREN;
      }
      if ("node_modules".equals(name)) {
        return DirectoryProcessingResult.SKIP_CHILDREN;
      }
    }
    return DirectoryProcessingResult.PROCESS_CHILDREN;
  }

  @Override
  public void setupProjectStructure(@NotNull Collection<DetectedProjectRoot> roots,
                                    @NotNull ProjectDescriptor projectDescriptor,
                                    @NotNull ProjectFromSourcesBuilder builder) {
    builder.setupModulesByContentRoots(projectDescriptor, roots);
  }

  @Override
  public List<ModuleWizardStep> createWizardSteps(ProjectFromSourcesBuilder builder, ProjectDescriptor projectDescriptor, Icon stepIcon) {
    return Collections.singletonList(ProjectWizardStepFactory.getInstance().createProjectJdkStep(builder.getContext()));
  }
}
