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
package com.jetbrains.python.packaging;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Function;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.remote.PyCredentialsContribution;
import com.jetbrains.python.sdk.CredentialsTypeExChecker;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author vlan
 */
public class PyPackageUtil {
  public static final String[] SETUP_PY_REQUIRES_KWARGS_NAMES = new String[] {
    "requires", "install_requires", "setup_requires", "tests_require"
  };

  private PyPackageUtil() {
  }

  @Nullable
  public static PyFile findSetupPy(@NotNull Module module) {
    for (VirtualFile root : PyUtil.getSourceRoots(module)) {
      final VirtualFile child = root.findChild("setup.py");
      if (child != null) {
        final PsiFile file = PsiManager.getInstance(module.getProject()).findFile(child);
        if (file instanceof PyFile) {
          return (PyFile)file;
        }
      }
    }
    return null;
  }

  @Nullable
  public static VirtualFile findRequirementsTxt(@NotNull Module module) {
    final String requirementsPath = PyPackageRequirementsSettings.getInstance(module).getRequirementsPath();
    if (!requirementsPath.isEmpty()) {
      final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(requirementsPath);
      if (file != null) {
        return file;
      }
      final ModuleRootManager manager = ModuleRootManager.getInstance(module);
      for (VirtualFile root : manager.getContentRoots()) {
        final VirtualFile fileInRoot = root.findFileByRelativePath(requirementsPath);
        if (fileInRoot != null) {
          return fileInRoot;
        }
      }
    }
    return null;
  }

  @Nullable
  public static PyListLiteralExpression findSetupPyRequires(@NotNull Module module) {
    for (String name : SETUP_PY_REQUIRES_KWARGS_NAMES) {
      final PyListLiteralExpression kwarg = findSetupPyRequires(module, name);
      if (kwarg != null) {
        return kwarg;
      }
    }
    return null;
  }

  @Nullable
  public static PyListLiteralExpression findSetupPyRequires(@NotNull Module module, @NotNull String kwargName) {
    final PyFile setupPy = findSetupPy(module);
    if (setupPy != null) {
      final PyCallExpression setup = findSetupCall(setupPy);
      if (setup != null) {
        for (PyExpression arg : setup.getArguments()) {
          if (arg instanceof PyKeywordArgument) {
            final PyKeywordArgument kwarg = (PyKeywordArgument)arg;
            if (kwargName.equals(kwarg.getKeyword())) {
              final PyExpression value = kwarg.getValueExpression();
              if (value instanceof PyListLiteralExpression) {
                return (PyListLiteralExpression)value;
              }
              if (value instanceof PyReferenceExpression) {
                final TypeEvalContext context = TypeEvalContext.deepCodeInsight(module.getProject());
                final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
                final QualifiedResolveResult result = ((PyReferenceExpression)value).followAssignmentsChain(resolveContext);
                final PsiElement element = result.getElement();
                if (element instanceof PyListLiteralExpression) {
                  return (PyListLiteralExpression)element;
                }
              }
            }
          }
        }
      }
    }
    return null;
  }

  @NotNull
  public static List<String> getPackageNames(@NotNull Module module) {
    // TODO: Cache found module packages, clear cache on module updates
    final List<String> packageNames = new ArrayList<String>();
    final Project project = module.getProject();
    VirtualFile[] roots = ModuleRootManager.getInstance(module).getSourceRoots();
    if (roots.length == 0) {
      roots = ModuleRootManager.getInstance(module).getContentRoots();
    }
    for (VirtualFile root : roots) {
      collectPackageNames(project, root, packageNames);
    }
    return packageNames;
  }

  @NotNull
  public static String requirementsToString(@NotNull List<PyRequirement> requirements) {
    return StringUtil.join(requirements, new Function<PyRequirement, String>() {
      @Override
      public String fun(PyRequirement requirement) {
        return String.format("'%s'", requirement.toString());
      }
    }, ", ");
  }

  @Nullable
  public static PyCallExpression findSetupCall(@NotNull PyFile file) {
    final Ref<PyCallExpression> result = new Ref<PyCallExpression>(null);
    file.acceptChildren(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyCallExpression(PyCallExpression node) {
        final PyExpression callee = node.getCallee();
        final String name = PyUtil.getReadableRepr(callee, true);
        if ("setup".equals(name)) {
          result.set(node);
        }
      }

      @Override
      public void visitPyElement(PyElement node) {
        if (!(node instanceof ScopeOwner)) {
          super.visitPyElement(node);
        }
      }
    });
    return result.get();
  }

  private static void collectPackageNames(@NotNull final Project project,
                                          @NotNull final VirtualFile root,
                                          @NotNull final List<String> results) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (!fileIndex.isExcluded(file) && file.isDirectory() && file.findChild(PyNames.INIT_DOT_PY) != null) {
          results.add(VfsUtilCore.getRelativePath(file, root, '.'));
        }
        return true;
      }
    });
  }

  public static boolean packageManagementEnabled(@Nullable Sdk sdk) {
    if (!PythonSdkType.isRemote(sdk)) {
      return true;
    }
    return new CredentialsTypeExChecker() {
      @Override
      protected boolean checkLanguageContribution(PyCredentialsContribution languageContribution) {
        return languageContribution.isPackageManagementEnabled();
      }
    }.withSshContribution(true).withVagrantContribution(true).withWebDeploymentContribution(true).check(sdk);
  }
}
