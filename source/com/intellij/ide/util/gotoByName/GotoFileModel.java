
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;

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
    String[] fileNames = PsiManager.getInstance(myProject).getShortNamesCache().getAllFileNames();

    ArrayList<String> array = new ArrayList<String>();
    for(int i = 0; i < fileNames.length; i++){
      String fileName = fileNames[i];
      FileType type = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
      if (type.isBinary()) continue;
      //if (!checkBoxState && (type == FileType.JAVA || type == FileType.CLASS)) continue;
      if (type == StdFileTypes.CLASS) continue;
      array.add(fileName);
    }

    return (String[])array.toArray(new String[array.size()]);
  }

  public Object[] getElementsByName(final String name, final boolean checkBoxState) {
    PsiFile[] psiFiles = PsiManager.getInstance(myProject).getShortNamesCache().getFilesByName(name);
    if (checkBoxState){
      return psiFiles;
    }
    else{
      ArrayList<PsiFile> list = new ArrayList<PsiFile>();
      for(int i = 0; i < psiFiles.length; i++){
        PsiFile file = psiFiles[i];
        if (file instanceof PsiJavaFile) continue;
        list.add(file);
      }
      return (PsiFile[])list.toArray(new PsiFile[list.size()]);
    }
  }

  public String getElementName(final Object element) {
    if (!(element instanceof PsiFile)) return null;
    return ((PsiFile)element).getName();
  }
}