// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.pyqt;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.fileTypes.INativeFileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * @author yole
 */
public abstract class QtFileType extends LanguageFileType implements INativeFileType {
  private final String myName;
  private final String myDescription;
  private final String myDefaultExtension;

  QtFileType(String name, String description, String defaultExtension) {
    super(XMLLanguage.INSTANCE);
    myName = name;
    myDescription = description;
    myDefaultExtension = defaultExtension;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getDescription() {
    return myDescription;
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return myDefaultExtension;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, @NotNull byte[] content) {
    return null;
  }

  @Override
  public boolean openFileInAssociatedApplication(Project project, @NotNull VirtualFile file) {
    String qtTool = findQtTool(ModuleUtilCore.findModuleForFile(file, project), getToolName());
    if (qtTool == null) {
      return false;
    }
    try {
      Runtime.getRuntime().exec(new String[] { qtTool, file.getPath() } );
    }
    catch (IOException e) {
      Messages.showErrorDialog(project, "Failed to run Qt Designer: " + e.getMessage(), "Error");
    }
    return true;
  }

  public static String findQtTool(Module module, String toolName) {
    if (SystemInfo.isWindows) {
      if (module == null) {
        return null;
      }
      Sdk sdk = PythonSdkType.findPythonSdk(module);
      if (sdk == null) {
        return null;
      }
      String tool = findToolInPackage(toolName, module, "PyQt4");
      if (tool != null) {
        return tool;
      }
      return findToolInPackage(toolName, module, "PySide");
   }
    // TODO
    return null;
  }

  @Nullable
  private static String findToolInPackage(String toolName, Module module, String name) {
    final List<PsiElement> results = PyResolveImportUtil.resolveQualifiedName(QualifiedName.fromDottedString(name),
                                                                              PyResolveImportUtil.fromModule(module));
    return StreamEx.of(results).select(PsiDirectory.class)
      .map(directory -> directory.getVirtualFile().findChild(toolName + ".exe"))
      .nonNull()
      .map(VirtualFile::getPath)
      .findFirst()
      .orElse(null);
  }

  protected abstract String getToolName();
}
