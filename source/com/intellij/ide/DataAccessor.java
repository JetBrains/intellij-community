package com.intellij.ide;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.Function;
import static com.intellij.util.containers.ContainerUtil.map;
import static com.intellij.util.containers.ContainerUtil.skipNulls;
import com.intellij.util.containers.Convertor;

import java.lang.reflect.Array;
import java.util.List;

public abstract class DataAccessor<T> {
  public static final DataAccessor<Project> PROJECT = new SimpleDataAccessor<Project>(DataConstants.PROJECT);
  public static final DataAccessor<Module> MODULE = new SimpleDataAccessor<Module>(DataConstants.MODULE);
  public static final DataAccessor<Editor> EDITOR = new SimpleDataAccessor<Editor>(DataConstants.EDITOR);

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

  public static final DataAccessor<PsiElement> PSI_ELEMENT = new SimpleDataAccessor<PsiElement>(DataConstants.PSI_ELEMENT);
  public static final DataAccessor<PsiElement[]> PSI_ELEMENT_ARRAY = new SimpleDataAccessor<PsiElement[]>(DataConstantsEx.PSI_ELEMENT_ARRAY);

  public static final DataAccessor<VirtualFile> VIRTUAL_FILE = new SimpleDataAccessor<VirtualFile>(DataConstants.VIRTUAL_FILE);
  public static final DataAccessor<VirtualFile[]> VIRTUAL_FILE_ARRAY = new SimpleDataAccessor<VirtualFile[]>(DataConstants.VIRTUAL_FILE_ARRAY);

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
      return containingDirectory.getPackage();
    }
  };

  public static final DataAccessor<PsiJavaFile> PSI_JAVA_FILE = SubClassDataAccessor.create(PSI_FILE, PsiJavaFile.class);

  public static final DataAccessor<String> PROJECT_FILE_PATH = new DataAccessor<String>() {
    public String getImpl(DataContext dataContext) throws NoDataException {
      Project project = PROJECT.getNotNull(dataContext);
      return project.getProjectFilePath();
    }
  };

  public static final DataAccessor<String> MODULE_FILE_PATH = new DataAccessor<String>() {
    public String getImpl(DataContext dataContext) throws NoDataException {
      Module module = MODULE.getNotNull(dataContext);
      return module.getModuleFilePath();
    }
  };

  public static final DataAccessor<ProjectEx> PROJECT_EX = new SubClassDataAccessor<Project, ProjectEx>(PROJECT, ProjectEx.class);

  public final T from(DataContext dataContext) {
    try {
      return getNotNull(dataContext);
    } catch(NoDataException e) {
      return null;
    }
  }

  protected abstract T getImpl(DataContext dataContext) throws NoDataException;

  public final T getNotNull(DataContext dataContext) throws NoDataException {
    T data = getImpl(dataContext);
    if (data == null) throw new NoDataException(toString());
    return data;
  }

  public static <T, Original> DataAccessor<T> createConvertor(final DataAccessor<Original> original,
                                                              final Function<Original, T> convertor) {
    return new DataAccessor<T>(){
      public T getImpl(DataContext dataContext) throws NoDataException {
        return convertor.fun(original.getNotNull(dataContext));
      }
    };
  }

  public static <T, Original> DataAccessor<T[]> createArrayConvertor(final DataAccessor<Original[]> original, final Function<Original, T> convertor, final Class<T> aClass) {
    return new DataAccessor<T[]>() {
      public T[] getImpl(DataContext dataContext) throws NoDataException {
        List<T> converted = skipNulls(map(original.getNotNull(dataContext), convertor));
        return converted.toArray((T[])Array.newInstance(aClass, converted.size()));
      }
    };
  }

  public static <T> DataAccessor<T> createConditionalAccessor(DataAccessor<T> accessor, Condition<T> condition) {
    return new ConditionalDataAccessor<T>(accessor, condition);
  }

  public static class SimpleDataAccessor<T> extends DataAccessor<T> {
    private final String myDataConstant;

    public SimpleDataAccessor(String dataConstant) {
      myDataConstant = dataConstant;
    }

    public T getImpl(DataContext dataContext) throws NoDataException {
      T data = (T)dataContext.getData(myDataConstant);
      if (data == null) throw new NoDataException(myDataConstant);
      return data;
    }
  }

  public static class SubClassDataAccessor<Super, Sub> extends DataAccessor<Sub> {
    private final DataAccessor<Super> myOriginal;
    private final Class<Sub> mySubClass;

    private SubClassDataAccessor(DataAccessor<Super> original, Class<Sub> subClass) {
      myOriginal = original;
      mySubClass = subClass;
    }

    public Sub getImpl(DataContext dataContext) throws NoDataException {
      Object data = myOriginal.getNotNull(dataContext);
      if (!mySubClass.isInstance(data)) return null;
      return (Sub)data;
    }

    public static <Super, Sub extends Super> DataAccessor<Sub> create(DataAccessor<Super> accessor, Class<Sub> subClass) {
      return new SubClassDataAccessor<Super, Sub>(accessor, subClass);
    }
  }

  private static class ConditionalDataAccessor<T> extends DataAccessor<T> {
    private final DataAccessor<T> myOriginal;
    private final Condition<T> myCondition;

    public ConditionalDataAccessor(DataAccessor<T> original, Condition<T> condition) {
      myOriginal = original;
      myCondition = condition;
    }

    public T getImpl(DataContext dataContext) throws NoDataException {
      T value = myOriginal.getNotNull(dataContext);
      return myCondition.value(value) ? value : null;
    }
  }

  public static class NoDataException extends Exception {
    public NoDataException(String missingData) {
      super(IdeBundle.message("exception.missing.data", missingData));
    }
  }
}
