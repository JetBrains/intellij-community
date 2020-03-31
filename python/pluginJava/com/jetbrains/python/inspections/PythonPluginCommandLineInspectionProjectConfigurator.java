// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.CommandLineInspectionProgressReporter;
import com.intellij.codeInspection.CommandLineInspectionProjectConfigurator;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.facet.PythonFacetType;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PythonSdkUpdater;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PythonPluginCommandLineInspectionProjectConfigurator implements CommandLineInspectionProjectConfigurator {
  @Override
  public boolean isApplicable(@NotNull Path projectPath, @NotNull CommandLineInspectionProgressReporter logger) {
    List<Sdk> sdks = PythonSdkUtil.getAllSdks();
    if (!sdks.isEmpty()) return false;

    try {
      boolean hasAnyPythonFiles = Files.walk(projectPath).anyMatch(f -> {
        return f.toString().endsWith(".py");
      });
      if (!hasAnyPythonFiles) {
        logger.reportMessage(3, "Skipping Python interpreter autodetection because the project doesn't contain any Python files");
      }

      return hasAnyPythonFiles;
    }
    catch (IOException e) {
      return false;
    }
  }

  @Override
  public void configureEnvironment(@NotNull Path projectPath, @NotNull CommandLineInspectionProgressReporter logger) {
    logger.reportMessage(3, "Python environment configuration...");
    List<Sdk> sdks = PythonSdkUtil.getAllSdks();
    logger.reportMessage(3, "Python interpreters detected:");
    for (Sdk sdk : sdks) {
      logger.reportMessage(3, sdk.getHomePath());
    }
    if (sdks.isEmpty()) {
      final List<Sdk> detectedSdks = PySdkExtKt.findAllPythonSdks(projectPath);

      if (detectedSdks.size() > 0) {
        for (Sdk sdk : detectedSdks) {
          logger.reportMessage(3, sdk.getHomePath());
        }
        final Sdk sdk = detectedSdks.get(0);
        ApplicationManager.getApplication().runWriteAction(() -> {
          logger.reportMessage(1, "Settings up interpreter " + sdk.getName());
          ProjectJdkTable.getInstance().addJdk(sdk);
        });
        PythonSdkUpdater.update(sdk, null, null, null);
      } else {
        logger.reportMessage(1, "ERROR: Can't find Python interpreter");
      }
    }

  }

  @Override
  public void configureProject(@NotNull Project project, @NotNull AnalysisScope scope, @NotNull CommandLineInspectionProgressReporter logger) {
    List<Sdk> sdks = PythonSdkUtil.getAllSdks();
    if (!sdks.isEmpty()) {
      PythonFacetType facetType = PythonFacetType.getInstance();
      for (VirtualFile f: scope.getFiles()) {
        if (FileTypeRegistry.getInstance().isFileOfType(f, PythonFileType.INSTANCE)) {

          Module m = ModuleUtilCore.findModuleForFile(f, project);
          if (m != null && FacetManager.getInstance(m).getFacetByType(facetType.getId()) == null) {
            ApplicationManager.getApplication().runWriteAction(() -> {
              FacetManager.getInstance(m).addFacet(facetType, facetType.getPresentableName(), null);
            });
          }
        }
      }
    }
  }
}
