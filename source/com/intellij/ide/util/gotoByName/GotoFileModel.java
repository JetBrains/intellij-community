package com.intellij.ide.util.gotoByName;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

import java.util.ArrayList;

public class GotoFileModel implements ChooseByNameModel {
  private final Project myProject;
  private final int myMaxSize;
  public GotoFileModel(Project project) {
    myProject = project;
    myMaxSize = WindowManagerEx.getInstanceEx().getFrame(myProject).getSize().width;
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
    return new GotoFileCellRenderer(myMaxSize);
  }

  public String[] getNames(boolean checkBoxState) {
    return PsiManager.getInstance(myProject).getShortNamesCache().getAllFileNames();
  }

  private boolean isEditable(PsiFile psiFile, final boolean checkboxState) {
    FileType type = psiFile.getFileType();
    if (!checkboxState && type == StdFileTypes.JAVA) return false;
    return type != StdFileTypes.CLASS;
  }

  public Object[] getElementsByName(final String name, final boolean checkBoxState) {
    PsiFile[] psiFiles = PsiManager.getInstance(myProject).getShortNamesCache().getFilesByName(name);
    ArrayList<PsiFile> list = new ArrayList<PsiFile>();
    for (PsiFile file : psiFiles) {
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