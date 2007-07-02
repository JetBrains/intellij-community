package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.injected.DocumentRange;
import com.intellij.openapi.editor.impl.injected.VirtualFileDelegate;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/**
 * @author cdr
 */
public class InjectedManagerImpl extends InjectedManager implements ApplicationComponent {
  private final WeakList<VirtualFileDelegate> cachedFiles = new WeakList<VirtualFileDelegate>();
  private final ProjectManagerAdapter myProjectListener;

  public static InjectedManagerImpl getInstance() {
    return (InjectedManagerImpl)InjectedManager.getInstance();
  }

  public InjectedManagerImpl() {
    myProjectListener = new ProjectManagerAdapter() {
      public void projectClosing(final Project project) {
        VirtualFileDelegate[] delegates;
        synchronized (cachedFiles) {
          delegates = new VirtualFileDelegate[cachedFiles.size()];
          Iterator<VirtualFileDelegate> iterator = cachedFiles.iterator();
          int i =0;
          while (iterator.hasNext()) {
            delegates[i++] = iterator.next();
          }
        }
        for (VirtualFileDelegate file : delegates) {
          if (file == null) continue;
          DocumentRange documentRange = file.getDocumentRange();
          PsiFile injected = PsiDocumentManager.getInstance(project).getCachedPsiFile(documentRange);
          if (injected != null) {
            InjectedLanguageUtil.clearCaches(injected, documentRange);
          }
        }
      }
    };
  }

  VirtualFileDelegate createVirtualFile(final Language language, final VirtualFile hostVirtualFile,
                                                                             final DocumentRange documentRange, StringBuilder text,
                                                                             Project project) {
    clearInvalidFiles(project);
    VirtualFileDelegate virtualFile = new VirtualFileDelegate(hostVirtualFile, documentRange, language, text.toString());
    synchronized (cachedFiles) {
      cachedFiles.add(virtualFile);
    }

    return virtualFile;
  }

  private void clearInvalidFiles(Project project) {
    synchronized (cachedFiles) {
      Iterator<VirtualFileDelegate> iterator = cachedFiles.iterator();
      while (iterator.hasNext()) {
        VirtualFileDelegate cachedFile = iterator.next();
        PsiFile cached = ((PsiManagerEx)PsiManager.getInstance(project)).getFileManager().getCachedPsiFile(cachedFile);
        if (cached == null || cached.getContext() == null || !cached.getContext().isValid()) {
          iterator.remove();
          continue;
        }

        Document cachedDocument = PsiDocumentManager.getInstance(project).getCachedDocument(cached);
        if (!(cachedDocument instanceof DocumentRange)) {
          iterator.remove();
        }
      }
    }
  }

  public PsiLanguageInjectionHost getInjectionHost(PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file == null) return null;
    return (PsiLanguageInjectionHost)file.getContext();
  }

  public TextRange injectedToHost(PsiElement element, TextRange textRange) {
    PsiFile file = element.getContainingFile();
    if (file == null) return textRange;
    Document document = PsiDocumentManager.getInstance(element.getProject()).getCachedDocument(file);
    if (!(document instanceof DocumentRange)) return textRange;
    DocumentRange documentRange = (DocumentRange)document;
    return new TextRange(documentRange.injectedToHost(textRange.getStartOffset()), documentRange.injectedToHost(textRange.getEndOffset()));
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "InjectdManager";
  }

  public void initComponent() {
    ProjectManager.getInstance().addProjectManagerListener(myProjectListener);
  }

  public void disposeComponent() {
    ProjectManager.getInstance().removeProjectManagerListener(myProjectListener);
  }

  public void clearCaches(VirtualFileDelegate virtualFile) {
    synchronized (cachedFiles) {
      cachedFiles.remove(virtualFile);
    }
  }
}
