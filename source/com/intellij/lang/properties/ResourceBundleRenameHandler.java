/**
 * @author Alexey
 */
package com.intellij.lang.properties;

import com.intellij.lang.properties.editor.ResourceBundleAsVirtualFile;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.codeInsight.CodeInsightUtil;

import java.io.File;
import java.util.List;

import org.jetbrains.annotations.Nullable;

public class ResourceBundleRenameHandler implements RenameHandler {
  public static final RenameHandler INSTANCE = new ResourceBundleRenameHandler();

  private ResourceBundleRenameHandler() {
  }

  public boolean isAvailableOnDataContext(DataContext dataContext) {
    return getResourceBundleFromDataContext(dataContext) != null;
  }

  public boolean isRenaming(DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    ResourceBundle resourceBundle = getResourceBundleFromDataContext(dataContext);

    assert resourceBundle != null;
    Messages.showInputDialog(project,
                             PropertiesBundle.message("rename.bundle.enter.new.resource.bundle.base.name.prompt.text"),
                             PropertiesBundle.message("rename.resource.bundle.dialog.title"),
                             Messages.getQuestionIcon(),
                             resourceBundle.getBaseName(),
                             new MyInputValidator(project, resourceBundle));
  }

  public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
    invoke(project, null, null, dataContext);
  }

  @Nullable
  private static ResourceBundle getResourceBundleFromDataContext(DataContext dataContext) {
    VirtualFile virtualFile = (VirtualFile)dataContext.getData(DataConstantsEx.VIRTUAL_FILE);
    if (virtualFile == null) {
      return null;
    }
    if (virtualFile instanceof ResourceBundleAsVirtualFile) {
      return ((ResourceBundleAsVirtualFile)virtualFile).getResourceBundle();
    }
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project != null) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
      if (psiFile instanceof PropertiesFile) {
        return ((PropertiesFile)psiFile).getResourceBundle();
      }
    }
    return null;
  }

  private static class MyInputValidator implements InputValidator {
    private final Project myProject;
    private final ResourceBundle myResourceBundle;

    public MyInputValidator(final Project project, final ResourceBundle resourceBundle) {
      myProject = project;
      myResourceBundle = resourceBundle;
    }

    public boolean checkInput(String inputString) {
      return inputString.indexOf(File.separatorChar) < 0 && inputString.indexOf('/') < 0;
    }

    public boolean canClose(final String inputString) {
      return doRename(inputString);
    }
    private boolean doRename(final String inputString) {
      final List<PropertiesFile> propertiesFiles = myResourceBundle.getPropertiesFiles(myProject);
      for (PropertiesFile propertiesFile : propertiesFiles) {
        if (!CodeInsightUtil.prepareFileForWrite(propertiesFile)) return false;
      }

      final RenameProcessor renameProcessor = new RenameProcessor(myProject, null);
      String baseName = myResourceBundle.getBaseName();
      for (PropertiesFile propertiesFile : propertiesFiles) {
        final VirtualFile virtualFile = propertiesFile.getVirtualFile();
        final String newName = inputString + virtualFile.getNameWithoutExtension().substring(baseName.length()) + "." + virtualFile
          .getExtension();
        renameProcessor.addElement(propertiesFile,  newName);
      }
      renameProcessor.doRun();
      final Ref<Boolean> success = Ref.create(Boolean.TRUE);
      return true;
    }
  }
}