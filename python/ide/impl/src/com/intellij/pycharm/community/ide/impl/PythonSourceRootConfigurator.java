// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectConfigurator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

final class PythonSourceRootConfigurator implements DirectoryProjectConfigurator {
  private static final @NonNls String SETUP_PY = "setup.py";
  private static final @NonNls String SRC_FOLDER = "src";

  @Override
  public void configureProject(@NotNull Project project, @NotNull VirtualFile baseDir, @NotNull Ref<Module> moduleRef, boolean isProjectCreatedWithWizard) {
    VirtualFile setupPy = baseDir.findChild(SETUP_PY);
    if (setupPy != null) {
      configureFromSetupPy(project, baseDir, setupPy);
    }

    VirtualFile srcFolder = baseDir.findChild(SRC_FOLDER);
    if (srcFolder != null) {
      configureSrcDirectory(project, baseDir, srcFolder);
    }
  }

  private static void configureSrcDirectory(@NotNull Project project, @NotNull VirtualFile baseDir, @NotNull VirtualFile srcFolder) {
    addSourceRoot(project, baseDir, srcFolder, false);
  }

  private static void configureFromSetupPy(@NotNull Project project, @NotNull VirtualFile baseDir, @NotNull VirtualFile setupPy) {
    CharSequence content = LoadTextUtil.loadText(setupPy);
    PsiElement setupPyFile = PsiFileFactory.getInstance(project).createFileFromText(SETUP_PY, PythonFileType.INSTANCE, content.toString());
    final SetupCallVisitor visitor = new SetupCallVisitor();
    setupPyFile.accept(visitor);
    String dir = visitor.getRootPackageDir();
    if (dir != null) {
      VirtualFile rootPackageVFile = baseDir.findFileByRelativePath(dir);
      addSourceRoot(project, baseDir, rootPackageVFile, true);
    }
  }

  private static void addSourceRoot(Project project, final VirtualFile baseDir, final VirtualFile root, final boolean unique) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    if (modules.length > 0 && root != null) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        final ModifiableRootModel model = ModuleRootManager.getInstance(modules[0]).getModifiableModel();
        final ContentEntry[] contentEntries = model.getContentEntries();
        for (ContentEntry contentEntry : contentEntries) {
          if (Comparing.equal(contentEntry.getFile(), baseDir)) {
            final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
            if (!unique || sourceFolders.length == 0) {
              contentEntry.addSourceFolder(root, false);
            }
          }
        }
        model.commit();
      });
    }
  }

  private static class SetupCallVisitor extends PyRecursiveElementVisitor {
    private String myRootPackageDir = null;

    @Override
    public void visitPyCallExpression(@NotNull PyCallExpression node) {
      final PyArgumentList argList = node.getArgumentList();
      if (node.isCalleeText("setup") && argList != null) {
        final PyKeywordArgument packageDirArg = argList.getKeywordArgument("package_dir");
        if (packageDirArg != null) {
          final PyExpression valueExpression = packageDirArg.getValueExpression();
          if (valueExpression instanceof PyDictLiteralExpression packageDirDict) {
            for (PyKeyValueExpression keyValue : packageDirDict.getElements()) {
              final PyExpression keyExpr = keyValue.getKey();
              final PyExpression valueExpr = keyValue.getValue();
              if (keyExpr instanceof PyStringLiteralExpression && valueExpr instanceof PyStringLiteralExpression) {
                String key = ((PyStringLiteralExpression) keyExpr).getStringValue();
                String value = ((PyStringLiteralExpression)valueExpr).getStringValue();
                if (key.isEmpty()) {
                  myRootPackageDir = value;
                }
              }
            }
          }
        }
      }
      else {
        super.visitPyCallExpression(node);
      }
    }

    public String getRootPackageDir() {
      return myRootPackageDir;
    }
  }
}
