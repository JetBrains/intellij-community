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
package com.jetbrains.edu.learning;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.PlatformUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.inspections.PyInspectionVisitor;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyStudyInterpreterInspection extends PyInspection {

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Wrong python interpreter";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        final boolean isOnTheFly,
                                        @NotNull final LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {

    public Visitor(@Nullable ProblemsHolder holder,
                   @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyFile(PyFile node) {
      super.visitPyFile(node);
      if (PlatformUtils.isPyCharmEducational()) {
        final Course course = StudyTaskManager.getInstance(node.getProject()).getCourse();
        if (course == null) return;

        final Module module = ModuleUtilCore.findModuleForPsiElement(node);
        if (module != null) {
          final Sdk sdk = PythonSdkType.findPythonSdk(module);
          if (sdk == null) return;
          final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdk);
          if (flavor == null) return;
          final String versionString = flavor.getVersionString(sdk.getHomePath());
          if (versionString == null) return;
          final String prefix = flavor.getName() + " ";
          if (!versionString.startsWith(prefix)) {
            return;
          }
          final LanguageLevel projectLanguageLevel = LanguageLevel.fromPythonVersion(versionString.substring(prefix.length()));

          final String version = course.getLanguageVersion();
          if (PyStudyLanguageManager.PYTHON_2.equals(version)) {
            if (projectLanguageLevel.isPy3K()) {
              registerProblem(node, "Course is available for Python 2, but Python 3 is selected as project interpreter", new ConfigureInterpreterFix());
            }
          }
          else if (PyStudyLanguageManager.PYTHON_3.equals(version)) {
            if (!projectLanguageLevel.isPy3K()) {
              registerProblem(node, "Course is available for Python 3, but Python 2 is selected as project interpreter", new ConfigureInterpreterFix());
            }
          }
          else if (version != null) {
            final LanguageLevel level = LanguageLevel.fromPythonVersion(version);
            if (!level.equals(projectLanguageLevel)) {
              registerProblem(node, "Course is available for Python " + level.toString() + ", but Python " + projectLanguageLevel.toString()
                                    + " is selected as project interpreter", new ConfigureInterpreterFix());
            }
          }
        }
      }
    }
  }

  private static class ConfigureInterpreterFix implements LocalQuickFix {
    @NotNull
    @Override
    public String getName() {
      return "Configure Python Interpreter";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Configure Python Interpreter";
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
      ApplicationManager.getApplication().invokeLater(() -> {
        // outside of read action
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "Project Interpreter");
      });
    }
  }
}
