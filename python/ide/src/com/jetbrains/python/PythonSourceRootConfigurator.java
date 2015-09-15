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
package com.jetbrains.python;

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
import com.intellij.psi.PsiFileFactory;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PythonSourceRootConfigurator implements DirectoryProjectConfigurator {
  @NonNls private static final String SETUP_PY = "setup.py";

  @Override
  public void configureProject(Project project, @NotNull VirtualFile baseDir, Ref<Module> moduleRef) {
    VirtualFile setupPy = baseDir.findChild(SETUP_PY);
    if (setupPy != null) {
      final CharSequence content = LoadTextUtil.loadText(setupPy);
      PyFile setupPyFile = (PyFile) PsiFileFactory.getInstance(project).createFileFromText(SETUP_PY, PythonFileType.INSTANCE, content.toString());
      final SetupCallVisitor visitor = new SetupCallVisitor();
      setupPyFile.accept(visitor);
      String dir = visitor.getRootPackageDir();
      if (dir != null) {
        final VirtualFile rootPackageVFile = baseDir.findFileByRelativePath(dir);
        addSourceRoot(project, baseDir, rootPackageVFile, true);
      }
    }

  }

  private static void addSourceRoot(Project project, final VirtualFile baseDir, final VirtualFile root, final boolean unique) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    if (modules.length > 0 && root != null) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
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
        }
      });
    }
  }

  private static class SetupCallVisitor extends PyRecursiveElementVisitor {
    private String myRootPackageDir = null;

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      final PyArgumentList argList = node.getArgumentList();
      if (node.isCalleeText("setup") && argList != null) {
        final PyKeywordArgument packageDirArg = argList.getKeywordArgument("package_dir");
        if (packageDirArg != null) {
          final PyExpression valueExpression = packageDirArg.getValueExpression();
          if (valueExpression instanceof PyDictLiteralExpression) {
            PyDictLiteralExpression packageDirDict = (PyDictLiteralExpression)valueExpression;
            for (PyKeyValueExpression keyValue : packageDirDict.getElements()) {
              final PyExpression keyExpr = keyValue.getKey();
              final PyExpression valueExpr = keyValue.getValue();
              if (keyExpr instanceof PyStringLiteralExpression && valueExpr instanceof PyStringLiteralExpression) {
                String key = ((PyStringLiteralExpression) keyExpr).getStringValue();
                String value = ((PyStringLiteralExpression)valueExpr).getStringValue();
                if (key.length() == 0) {
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
