package com.intellij.ide.hierarchy.actions;

import com.intellij.aspects.psi.PsiAspect;
import com.intellij.aspects.psi.PsiAspectFile;
import com.intellij.ide.hierarchy.HierarchyBrowserManager;
import com.intellij.ide.hierarchy.type.SubtypesHierarchyTreeStructure;
import com.intellij.ide.hierarchy.type.TypeHierarchyBrowser;
import com.intellij.ide.hierarchy.type.TypeHierarchyTreeStructure;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;

import java.awt.*;

public final class BrowseTypeHierarchyAction extends AnAction {
  public final void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments(); // prevents problems with smart pointers creation

    final PsiClass psiClass = getPsiClass(dataContext);
    if (psiClass == null) return;
    final TypeHierarchyBrowser hierarchyBrowser = new TypeHierarchyBrowser(project, psiClass);

    final Content content;

    final HierarchyBrowserManager hierarchyBrowserManager = project.getComponent(HierarchyBrowserManager.class);

    final ContentManager contentManager = hierarchyBrowserManager.getContentManager();
    final Content selectedContent = contentManager.getSelectedContent();
    if (selectedContent != null && !selectedContent.isPinned()) {
      content = selectedContent;
      final Component component = content.getComponent();
      if (component instanceof TypeHierarchyBrowser) {
        ((TypeHierarchyBrowser)component).dispose();
      }
      content.setComponent(hierarchyBrowser);
    }
    else {
      content = PeerFactory.getInstance().getContentFactory().createContent(hierarchyBrowser, null, true);
      contentManager.addContent(content);
      contentManager.addContentManagerListener(new ContentManagerAdapter() {
        public void contentRemoved(final ContentManagerEvent event) {
          final Content content = event.getContent();
          final Component component = content.getComponent();
          if (component instanceof TypeHierarchyBrowser) {
            ((TypeHierarchyBrowser)component).dispose();
            content.release();
          }
        }
      });
    }
    contentManager.setSelectedContent(content);
    hierarchyBrowser.setContent(content);

    final String name = psiClass.getName();
    content.setDisplayName(name);

    final Runnable runnable = new Runnable() {
      public void run() {
        final String typeName = psiClass.isInterface() ? SubtypesHierarchyTreeStructure.TYPE : TypeHierarchyTreeStructure.TYPE;
        hierarchyBrowser.changeView(typeName);
      }};
    ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.HIERARCHY).activate(runnable);
//    new Alarm().addRequest(runnable, 300);
  }

  public final void update(final AnActionEvent event){
    final Presentation presentation = event.getPresentation();
    if (!ActionPlaces.MAIN_MENU.equals(event.getPlace())) {
      presentation.setText("Browse Type Hierarchy");
    }

    final DataContext dataContext = event.getDataContext();
    final Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    if (editor != null) {
      final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
      final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      final boolean enabled = file instanceof PsiJavaFile || file instanceof JspFile;
      presentation.setVisible(enabled);
      presentation.setEnabled(enabled);
    }
    else {
      final boolean enabled = getPsiClass(dataContext) != null;
      presentation.setEnabled(enabled);
      presentation.setVisible(enabled);
    }
  }

  private static PsiClass getPsiClass(final DataContext dataContext) {
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) return null;

    final Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    if (editor != null) {
      final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null) return null;

      final int offset = editor.getCaretModel().getOffset();
      PsiElement element = file.findElementAt(offset);
      while (element != null) {
        if (element instanceof PsiFile) {
          if (!(element instanceof PsiJavaFile)) return null;
          if (element instanceof PsiAspectFile) {
            final PsiAspect[] aspects = ((PsiAspectFile) element).getAspects();
            return aspects.length == 1 ? aspects[0] : null;
          }
          final PsiClass[] classes = ((PsiJavaFile)element).getClasses();
          return classes.length == 1 ? classes[0] : null;
        }
        if (element instanceof PsiClass && !(element instanceof PsiAnonymousClass)) {
          return (PsiClass)element;
        }
        element = element.getParent();
      }

      return null;
    }
    else {
      final PsiElement element = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
      return element instanceof PsiClass ? (PsiClass)element : null;
    }
  }
}