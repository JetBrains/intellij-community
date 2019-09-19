// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.analysis.AnalysisScope;
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

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class PythonPluginCommandLineInspectionProjectConfigurator implements CommandLineInspectionProjectConfigurator {
  @Override
  public boolean isApplicable(Path projectPath) {
    System.out.println("Python is here");
    try {
      return Files.walk(projectPath).anyMatch(f -> {
        return f.toString().endsWith(".py");
      });
    }
    catch (IOException e) {
      return false;
    }
  }

  @Override
  public void configureEnvironment() {
    System.out.println("Python environment configuration...");
    List<Sdk> sdks = PythonSdkUtil.getAllSdks();
    System.out.println("Python interpreters detected:");
    for (Sdk sdk : sdks) {
      System.out.println(sdk.getHomePath());
    }
    if (sdks.isEmpty()) {
      final List<Sdk> detectedSdks = PySdkExtKt.findAllPythonSdks();

      if (detectedSdks.size() > 0) {
        for (Sdk sdk : detectedSdks) {
          System.out.println(sdk.getHomePath());
        }
        final Sdk sdk = detectedSdks.get(0);
        ApplicationManager.getApplication().runWriteAction(() -> {

          System.out.println("Settings up interpreter " + sdk.getName());
          ProjectJdkTable.getInstance().addJdk(sdk);
        });
        PythonSdkUpdater.update(sdk, null, null, null);
      } else {
        System.out.println("ERROR: Can't find Python interpreter");
      }
    }

  }

  @Override
  public void configureProject(@NotNull Project project, AnalysisScope scope) {
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
