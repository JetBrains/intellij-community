package com.intellij.uiDesigner.binding;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
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
  @NonNls public static final ID<String, Void> NAME = ID.create("FormClassIndex");
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
    return findFormsBoundToClass(project, className, ProjectScope.getAllScope(project));
  }

  public static List<PsiFile> findFormsBoundToClass(final Project project,
                                                    final String className,
                                                    final GlobalSearchScope scope) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    final VirtualFileFilter filter = new VirtualFileFilter() {
      public boolean accept(final VirtualFile file) {
        return index.isInContent(file) && scope.contains(file);
      }
    };

    return ApplicationManager.getApplication().runReadAction(new Computable<List<PsiFile>>() {
      public List<PsiFile> compute() {
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
    });
  }

  public static List<PsiFile> findFormsBoundToClass(PsiClass psiClass) {
    String qName = FormReferencesSearcher.getQualifiedName(psiClass);
    if (qName == null) return Collections.emptyList();
    return findFormsBoundToClass(psiClass.getProject(), qName);
  }

  public static List<PsiFile> findFormsBoundToClass(PsiClass psiClass, GlobalSearchScope scope) {
    String qName = FormReferencesSearcher.getQualifiedName(psiClass);
    if (qName == null) return Collections.emptyList();
    return findFormsBoundToClass(psiClass.getProject(), qName, scope);
  }
}
