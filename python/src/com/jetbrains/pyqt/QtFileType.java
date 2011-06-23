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
    if (SystemInfo.isWindows) {
      Module module = ModuleUtil.findModuleForFile(file, project);
      if (module == null) {
        return false;
      }
      Sdk sdk = PythonSdkType.findPythonSdk(module);
      if (sdk == null) {
        return false;
      }
      List<PsiElement> qt4 =
        ResolveImportUtil.resolveModulesInRootProvider(sdk.getRootProvider(), module, PyQualifiedName.fromComponents("PyQt4"));
      for (PsiElement psiElement : qt4) {
        if (psiElement instanceof PsiDirectory) {
          VirtualFile designer = ((PsiDirectory)psiElement).getVirtualFile().findChild(getToolName() + ".exe");
          if (designer != null) {
            try {
              Runtime.getRuntime().exec(new String[] { designer.getPath(), file.getPath() } );
            }
            catch (IOException e) {
              Messages.showErrorDialog(project, "Failed to run Qt Designer: " + e.getMessage(), "Error");
            }
            return true;
          }
        }
      }

   }
    // TODO
    return true;
  }

  protected abstract String getToolName();
}
