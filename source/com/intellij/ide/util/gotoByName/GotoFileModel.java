
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.PsiShortNamesCache;

import java.util.ArrayList;

public class GotoFileModel implements ChooseByNameModel{
  private final Project myProject;

  public GotoFileModel(Project project) {
    myProject = project;
  }

  public String getPromptText() {
    return "Enter file name:";
  }

  public String getCheckBoxName() {
    return "Include java files";
  }

  public char getCheckBoxMnemonic() {
    return 'j';
  }

  public String getNotInMessage() {
    return "no non-.java files found";
  }

  public String getNotFoundMessage() {
    return "no files found";
  }
  public boolean loadInitialCheckBoxState() {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    return "true".equals(propertiesComponent.getValue("GoToClass.includeJavaFiles"));
  }

  public void saveInitialCheckBoxState(boolean state) {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    propertiesComponent.setValue("GoToClass.includeJavaFiles", state ? "true" : "false");
  }

  public PsiElementListCellRenderer getListCellRenderer() {
    return new GotoFileCellRenderer();
  }

  public String[] getNames(boolean checkBoxState) {
    return PsiManager.getInstance(myProject).getShortNamesCache().getAllFileNames();
  }

  private boolean isEditable(PsiFile psiFile, final boolean checkboxState) {
    FileType type = psiFile.getFileType();
    if (!checkboxState && type == StdFileTypes.JAVA) return false;
    if (type == StdFileTypes.CLASS) return false; // Optimization
    if (!type.isBinary()) return true; // Optimization

    final FileEditorProviderManager editorProvider = FileEditorProviderManager.getInstance();
    VirtualFile vFile = psiFile.getVirtualFile();
    return vFile != null && editorProvider.getProviders(myProject, vFile).length > 0;
  }

  public Object[] getElementsByName(final String name, final boolean checkBoxState) {
    PsiFile[] psiFiles = PsiManager.getInstance(myProject).getShortNamesCache().getFilesByName(name);
    ArrayList<PsiFile> list = new ArrayList<PsiFile>();
    for(int i = 0; i < psiFiles.length; i++){
      PsiFile file = psiFiles[i];
      if (isEditable(file, checkBoxState)) {
        list.add(file);
      }
    }

    return list.toArray(new PsiFile[list.size()]);
  }

  public String getElementName(final Object element) {
    if (!(element instanceof PsiFile)) return null;
    return ((PsiFile)element).getName();
  }
}