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
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.pipenv.PipenvKt;
import com.jetbrains.python.sdk.pipenv.UsePipEnvQuickFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
      final Sdk sdk = PythonSdkType.findPythonSdk(module);

      final boolean pyCharm = PlatformUtils.isPyCharm();

      final String interpreterOwner = pyCharm ? "project" : "module";
      final List<LocalQuickFix> fixes = new ArrayList<>();
      // TODO: Introduce an inspection extension
      if (UsePipEnvQuickFix.Companion.isApplicable(module)) {
        fixes.add(new UsePipEnvQuickFix(sdk, module));
      }
      if (pyCharm) {
        fixes.add(new ConfigureInterpreterFix());
      }

      final String product = pyCharm ? "PyCharm" : "Python plugin";

      if (sdk == null) {
        registerProblem(node, "No Python interpreter configured for the " + interpreterOwner, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      }
      else {
        final Module associatedModule = PySdkExtKt.getAssociatedModule(sdk);
        final String associatedName = associatedModule != null ? associatedModule.getName() : PySdkExtKt.getAssociatedModulePath(sdk);
        // TODO: Introduce an inspection extension
        if (PipenvKt.isPipEnv(sdk) && associatedModule != module) {
          final String message = associatedName != null ?
                                 "Pipenv interpreter is associated with another " + interpreterOwner + ": '" + associatedName + "'" :
                                 "Pipenv interpreter is not associated with any " + interpreterOwner;
          registerProblem(node, message, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
        }
        else if (PythonSdkType.isInvalid(sdk)) {
          registerProblem(node, "Invalid Python interpreter selected for the " + interpreterOwner, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
        }
        else {
          final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(sdk);
          if (!LanguageLevel.SUPPORTED_LEVELS.contains(languageLevel)) {
            registerProblem(node,
                            "Python " + languageLevel + " has reached its end-of-life and is no longer supported by " + product,
                            fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
          }
        }
      }
    }
  }

  public static final class ConfigureInterpreterFix implements LocalQuickFix {
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
