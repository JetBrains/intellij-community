// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.analysis.AnalysisScope;
import com.intellij.facet.FacetManager;
import com.intellij.ide.CommandLineInspectionProgressReporter;
import com.intellij.ide.CommandLineInspectionProjectConfigurator;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.facet.PythonFacetType;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PythonSdkUpdater;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class PythonPluginCommandLineInspectionProjectConfigurator implements CommandLineInspectionProjectConfigurator {
  @Override
  public @NotNull String getName() {
    return "python";
  }

  @Override
  public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getDescription() {
    return PyBundle.message("py.commandline.configure");
  }

  @Override
  public boolean isApplicable(@NotNull ConfiguratorContext context) {
    List<Sdk> sdks = PythonSdkUtil.getAllSdks();
    if (!sdks.isEmpty()) return false;

    try {
      boolean hasAnyPythonFiles = Files.walk(context.getProjectPath()).anyMatch(f -> {
        return f.toString().endsWith(".py");
      });
      if (!hasAnyPythonFiles) {
        context.getLogger().reportMessage(3, "Skipping Python interpreter autodetection because the project doesn't contain any Python files");
      }

      return hasAnyPythonFiles;
    }
    catch (IOException e) {
      return false;
    }
  }

  @Override
  public void configureEnvironment(@NotNull ConfiguratorContext context) {
    CommandLineInspectionProgressReporter logger = context.getLogger();
    logger.reportMessage(3, "Python environment configuration...");
    List<Sdk> sdks = PythonSdkUtil.getAllSdks();
    logger.reportMessage(3, "Python interpreters detected:");
    for (Sdk sdk : sdks) {
      logger.reportMessage(3, sdk.getHomePath());
    }
    if (sdks.isEmpty()) {
      final List<Sdk> detectedSdks = PySdkExtKt.findAllPythonSdks(context.getProjectPath());

      if (detectedSdks.size() > 0) {
        for (Sdk sdk : detectedSdks) {
          logger.reportMessage(3, sdk.getHomePath());
        }
        final Sdk sdk = detectedSdks.get(0);
        WriteAction.runAndWait(() -> {
          logger.reportMessage(1, "Settings up interpreter " + sdk.getName());
          ProjectJdkTable.getInstance().addJdk(sdk);
        });

        PythonSdkUpdater.updateVersionAndPathsSynchronouslyAndScheduleRemaining(sdk, null);
      }
      else {
        logger.reportMessage(1, "ERROR: Can't find Python interpreter");
      }
    }
  }

  @Override
  public void configureProject(@NotNull Project project, @NotNull ConfiguratorContext context) {
    List<Sdk> sdks = PythonSdkUtil.getAllSdks();
    if (sdks.isEmpty()) return;

    AnalysisScope scope = context.getAnalyzerScope();
    if (scope == null) return;

    PythonFacetType facetType = PythonFacetType.getInstance();
    for (VirtualFile f : scope.getFiles()) {
      if (FileTypeRegistry.getInstance().isFileOfType(f, PythonFileType.INSTANCE)) {

        Module m = ModuleUtilCore.findModuleForFile(f, project);
        if (m != null && FacetManager.getInstance(m).getFacetByType(facetType.getId()) == null) {
          WriteAction.runAndWait(() -> {
            FacetManager.getInstance(m).addFacet(facetType, facetType.getPresentableName(), null);
          });
        }
      }
    }
  }
}
