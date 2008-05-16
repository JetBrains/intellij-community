package com.intellij.uiDesigner.binding;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentEnumerator;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author yole
 */
public class FormClassIndex extends ScalarIndexExtension<String> {
  @NonNls public static final ID<String, Void> NAME = new ID<String, Void>("FormClassIndex");
  private final EnumeratorStringDescriptor myKeyDescriptor = new EnumeratorStringDescriptor();
  private MyInputFilter myInputFilter = new MyInputFilter();
  private MyDataIndexer myDataIndexer = new MyDataIndexer();

  public ID<String, Void> getName() {
    return NAME;
  }

  public DataIndexer<String, Void, FileContent> getIndexer() {
    return myDataIndexer;
  }

  public PersistentEnumerator.DataDescriptor<String> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  public boolean dependsOnFileContent() {
    return true;
  }

  public int getVersion() {
    return 0;
  }

  private static class MyDataIndexer implements DataIndexer<String, Void, FileContent> {
    public Map<String, Void> map(final FileContent inputData) {
      String className = null;
      try {
        className = Utils.getBoundClassName(inputData.getContentAsText().toString());
      }
      catch (Exception e) {
        // ignore
      }
      if (className != null) {
        return Collections.singletonMap(className, null);
      }
      return Collections.emptyMap();
    }
  }

  private static class MyInputFilter implements FileBasedIndex.InputFilter {
    public boolean acceptInput(final VirtualFile file) {
      return file.getFileType() == StdFileTypes.GUI_DESIGNER_FORM;
    }
  }

  public static List<PsiFile> findFormsBoundToClass(Project project, String className) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    VirtualFileFilter filter = new VirtualFileFilter() {
      public boolean accept(final VirtualFile file) {
        return index.isInContent(file);
      }
    };

    final Collection<VirtualFile> files = FileBasedIndex.getInstance().getContainingFiles(NAME, className, filter);
    if (files.isEmpty()) return Collections.emptyList();
    List<PsiFile> result = new ArrayList<PsiFile>();
    for(VirtualFile file: files) {
      if (!file.isValid()) continue;
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile != null) {
        result.add(psiFile);
      }
    }
    return result;
  }

  public static List<PsiFile> findFormsBoundToClass(PsiClass psiClass) {
    String qName = psiClass.getQualifiedName();
    if (qName == null) return Collections.emptyList();
    return findFormsBoundToClass(psiClass.getProject(), psiClass.getQualifiedName());
  }
}
