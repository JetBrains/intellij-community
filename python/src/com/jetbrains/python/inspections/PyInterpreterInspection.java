/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.PlatformUtils;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyInterpreterInspection extends PyInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.invalid.interpreter");
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
      final Module module = ModuleUtilCore.findModuleForPsiElement(node);
      if (module == null) return;

      final boolean pyCharm = PlatformUtils.isPyCharm();

      final String interpreterOwner = pyCharm ? "project" : "module";
      final LocalQuickFix[] fixes = pyCharm ? new LocalQuickFix[]{new ConfigureInterpreterFix()} : LocalQuickFix.EMPTY_ARRAY;
      final String product = pyCharm ? "PyCharm" : "Python plugin";

      final Sdk sdk = PythonSdkType.findPythonSdk(module);

      if (sdk == null) {
        registerProblem(node, "No Python interpreter configured for the " + interpreterOwner, fixes);
      }
      else if (PythonSdkType.isInvalid(sdk)) {
        registerProblem(node, "Invalid Python interpreter selected for the " + interpreterOwner, fixes);
      }
      else {
        final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(sdk);
        if (!LanguageLevel.SUPPORTED_LEVELS.contains(languageLevel)) {
          registerProblem(node,
                          "Python " + languageLevel + " has reached its end-of-life and is no longer supported by " + product,
                          fixes);
        }
      }
    }
  }

  private static class ConfigureInterpreterFix implements LocalQuickFix {

    @NotNull
    @Override
    public String getFamilyName() {
      return "Configure Python interpreter";
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, "Project Interpreter");
    }
  }
}
