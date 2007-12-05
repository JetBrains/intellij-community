package com.intellij.ide;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

/**
 * @author yole
 */
public class DataAccessors {
  public static final DataAccessor<Project> PROJECT = new DataAccessor.SimpleDataAccessor<Project>(DataConstants.PROJECT);
  public static final DataAccessor<Module> MODULE = new DataAccessor.SimpleDataAccessor<Module>(DataConstants.MODULE);
  public static final DataAccessor<Editor> EDITOR = new DataAccessor.SimpleDataAccessor<Editor>(DataConstants.EDITOR);
  public static final DataAccessor<PsiManager> PSI_MANAGER = new DataAccessor<PsiManager>() {
    public PsiManager getImpl(DataContext dataContext) throws NoDataException {
      return PsiManager.getInstance(PROJECT.getNotNull(dataContext));
    }
  };
  public static final DataAccessor<FileEditorManager> FILE_EDITOR_MANAGER = new DataAccessor<FileEditorManager>() {
    public FileEditorManager getImpl(DataContext dataContext) throws NoDataException {
      return FileEditorManager.getInstance(PROJECT.getNotNull(dataContext));
    }
  };
  public static final DataAccessor<PsiFile> PSI_FILE = new DataAccessor<PsiFile>() {
    public PsiFile getImpl(DataContext dataContext) throws NoDataException {
      return PSI_MANAGER.getNotNull(dataContext).findFile(VIRTUAL_FILE.getNotNull(dataContext));
    }
  };
  public static final DataAccessor<PsiElement> PSI_ELEMENT = new DataAccessor.SimpleDataAccessor<PsiElement>(DataConstants.PSI_ELEMENT);
  public static final DataAccessor<PsiElement[]> PSI_ELEMENT_ARRAY = new DataAccessor.SimpleDataAccessor<PsiElement[]>(DataConstants.PSI_ELEMENT_ARRAY);
  public static final DataAccessor<VirtualFile> VIRTUAL_FILE = new DataAccessor.SimpleDataAccessor<VirtualFile>(DataConstants.VIRTUAL_FILE);
  public static final DataAccessor<VirtualFile[]> VIRTUAL_FILE_ARRAY = new DataAccessor.SimpleDataAccessor<VirtualFile[]>(DataConstants.VIRTUAL_FILE_ARRAY);
  public static final DataAccessor<VirtualFile> VIRTUAL_DIR_OR_PARENT = new DataAccessor<VirtualFile>() {
    public VirtualFile getImpl(DataContext dataContext) throws NoDataException {
      VirtualFile virtualFile = VIRTUAL_FILE.getNotNull(dataContext);
      return virtualFile.isDirectory() ? virtualFile : virtualFile.getParent();
    }
  };
  public static final DataAccessor<PsiPackage> FILE_PACKAGE = new DataAccessor<PsiPackage>() {
    public PsiPackage getImpl(DataContext dataContext) throws NoDataException {
      PsiFile psiFile = PSI_FILE.getNotNull(dataContext);
      PsiDirectory containingDirectory = psiFile.getContainingDirectory();
      if (containingDirectory == null || !containingDirectory.isValid()) return null;
      return JavaDirectoryService.getInstance().getPackage(containingDirectory);
    }
  };
  public static final DataAccessor<PsiJavaFile> PSI_JAVA_FILE = DataAccessor.SubClassDataAccessor.create(PSI_FILE, PsiJavaFile.class);
  /**
   * @deprecated
   */
  public static final DataAccessor<String> PROJECT_FILE_PATH = new DataAccessor<String>() {
    public String getImpl(DataContext dataContext) throws NoDataException {
      Project project = PROJECT.getNotNull(dataContext);
      return project.getProjectFilePath();
    }
  };
  public static final DataAccessor<VirtualFile> PROJECT_BASE_DIR = new DataAccessor<VirtualFile>() {
    public VirtualFile getImpl(DataContext dataContext) throws NoDataException {
      Project project = PROJECT.getNotNull(dataContext);
      return project.getBaseDir();
    }
  };
  public static final DataAccessor<String> MODULE_FILE_PATH = new DataAccessor<String>() {
    public String getImpl(DataContext dataContext) throws NoDataException {
      Module module = MODULE.getNotNull(dataContext);
      return module.getModuleFilePath();
    }
  };
  public static final DataAccessor<ProjectEx> PROJECT_EX = new DataAccessor.SubClassDataAccessor<Project, ProjectEx>(PROJECT, ProjectEx.class);
}