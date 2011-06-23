package com.jetbrains.pyqt;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.INativeFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;
import java.util.List;

/**
 * @author yole
 */
public abstract class QtFileType implements FileType, INativeFileType {
  private final String myName;
  private final String myDescription;
  private final String myDefaultExtension;
  private final Icon myIcon;

  protected QtFileType(String name, String description, String defaultExtension, Icon icon) {
    myName = name;
    myDescription = description;
    myDefaultExtension = defaultExtension;
    myIcon = icon;
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
  public Icon getIcon() {
    return myIcon;
  }

  @Override
  public boolean isBinary() {
    return true;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, byte[] content) {
    return null;
  }

  @Override
  public boolean openFileInAssociatedApplication(Project project, VirtualFile file) {
    String qtTool = findQtTool(ModuleUtil.findModuleForFile(file, project), getToolName());
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
      String tool = findToolInPackage(toolName, module, sdk, "PyQt4");
      if (tool != null) {
        return tool;
      }
      return findToolInPackage(toolName, module, sdk, "PySide");
   }
    // TODO
    return null;
  }

  private static String findToolInPackage(String toolName, Module module, Sdk sdk, String name) {
    List<PsiElement> elements = ResolveImportUtil.resolveModulesInRootProvider(sdk.getRootProvider(), module, PyQualifiedName.fromComponents(name));
    for (PsiElement psiElement : elements) {
      if (psiElement instanceof PsiDirectory) {
        VirtualFile tool = ((PsiDirectory)psiElement).getVirtualFile().findChild(toolName + ".exe");
        if (tool != null) {
          return tool.getPath();
        }
      }
    }
    return null;
  }

  protected abstract String getToolName();
}
