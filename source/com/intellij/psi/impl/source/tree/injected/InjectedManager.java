package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.impl.injected.DocumentRange;
import com.intellij.openapi.editor.impl.injected.VirtualFileDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/**
 * @author cdr
 */
public class InjectedManager implements ApplicationComponent {
  private final WeakList<VirtualFileDelegate> cachedFiles = new WeakList<VirtualFileDelegate>();
  private final ProjectManagerAdapter myProjectListener;

  public static InjectedManager getInstance() {
    return ApplicationManager.getApplication().getComponent(InjectedManager.class);
  }

  public InjectedManager() {
    myProjectListener = new ProjectManagerAdapter() {
      public void projectClosing(final Project project) {
        Iterator<VirtualFileDelegate> iterator = cachedFiles.iterator();
        while (iterator.hasNext()) {
          VirtualFileDelegate file = iterator.next();
          DocumentRange documentRange = file.getDocumentRange();
          PsiFile injected = PsiDocumentManager.getInstance(project).getCachedPsiFile(documentRange);
          if (injected != null) {
            InjectedLanguageUtil.clearCaches(injected, documentRange);
            iterator.remove();
          }
        }
      }
    };
  }

  <T extends PsiLanguageInjectionHost> VirtualFileDelegate createVirtualFile(final Language language, final VirtualFile hostVirtualFile,
                                                                             final DocumentRange documentRange, StringBuilder text,
                                                                             Project project) {
    //clearInvalidFiles(documentRange, project);

    VirtualFileDelegate virtualFile = new VirtualFileDelegate(hostVirtualFile, documentRange, language, text.toString());
    cachedFiles.add(virtualFile);

    return virtualFile;
  }

  private <T extends PsiLanguageInjectionHost> void clearInvalidFiles(DocumentRange documentRange, Project project) {
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
        continue;
      }
      DocumentRange cachedDocumentRange = (DocumentRange)cachedDocument;
      if (documentRange.equalsTo(cachedDocumentRange)) {
        InjectedLanguageUtil.clearCaches(cached, cachedDocumentRange);
        iterator.remove();
      }
    }
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
}
