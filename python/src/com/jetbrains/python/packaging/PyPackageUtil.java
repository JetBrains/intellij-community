package com.jetbrains.python.packaging;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Function;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author vlan
 */
public class PyPackageUtil {
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
  public static Document findRequirementsTxt(@NotNull Module module) {
    for (VirtualFile root : PyUtil.getSourceRoots(module)) {
      final VirtualFile child = root.findChild("requirements.txt");
      if (child != null) {
        return FileDocumentManager.getInstance().getDocument(child);
      }
    }
    return null;
  }

  @Nullable
  public static PyListLiteralExpression findSetupPyInstallRequires(@NotNull Module module) {
    final PyFile setupPy = findSetupPy(module);
    if (setupPy != null) {
      final PyCallExpression setup = findSetupCall(setupPy);
      if (setup != null) {
        for (PyExpression arg : setup.getArguments()) {
          if (arg instanceof PyKeywordArgument) {
            final PyKeywordArgument kwarg = (PyKeywordArgument)arg;
            if ("install_requires".equals(kwarg.getKeyword())) {
              final PyExpression value = kwarg.getValueExpression();
              if (value instanceof PyListLiteralExpression) {
                return (PyListLiteralExpression)value;
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
    for (VirtualFile root : PyUtil.getSourceRoots(module)) {
      collectPackageNames(project, root, "", packageNames);
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

  private static void collectPackageNames(@NotNull Project project, @NotNull VirtualFile root, @NotNull String prefix,
                                          @NotNull List<String> results) {
    for (VirtualFile child : root.getChildren()) {
      if (ProjectRootManager.getInstance(project).getFileIndex().isIgnored(child)) {
        continue;
      }
      if (child.findChild(PyNames.INIT_DOT_PY) != null) {
        final String name = prefix + child.getName();
        results.add(name);
        collectPackageNames(project, child, name + ".", results);
      }
    }
  }
}
