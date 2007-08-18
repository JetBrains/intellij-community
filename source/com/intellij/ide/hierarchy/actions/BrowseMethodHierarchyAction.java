package com.intellij.ide.hierarchy.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.HierarchyBrowserManager;
import com.intellij.ide.hierarchy.method.MethodHierarchyBrowser;
import com.intellij.ide.hierarchy.method.MethodHierarchyTreeStructure;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;

import java.awt.*;

public final class BrowseMethodHierarchyAction extends AnAction {
  public final void actionPerformed(final AnActionEvent e){
    final DataContext dataContext = e.getDataContext();
    final Project project = DataKeys.PROJECT.getData(dataContext);
    PsiDocumentManager.getInstance(project).commitAllDocuments(); // prevents problems with smart pointers creation
    final PsiMethod method = getMethod(dataContext);
    final MethodHierarchyBrowser hierarchyBrowser = new MethodHierarchyBrowser(project, method);

    final Content content;

    final HierarchyBrowserManager hierarchyBrowserManager = project.getComponent(HierarchyBrowserManager.class);

    final ContentManager contentManager = hierarchyBrowserManager.getContentManager();
    final Content selectedContent = contentManager.getSelectedContent();
    if (selectedContent != null && !selectedContent.isPinned()) {
      content = selectedContent;
      final Component component = content.getComponent();
      if (component instanceof MethodHierarchyBrowser) {
        ((MethodHierarchyBrowser)component).dispose();
      }
      content.setComponent(hierarchyBrowser);
    }
    else {
      content = PeerFactory.getInstance().getContentFactory().createContent(hierarchyBrowser, null, true);
      contentManager.addContent(content);
      contentManager.addContentManagerListener(new ContentManagerAdapter() {
        public void contentRemoved(final ContentManagerEvent event){
          final Content content = event.getContent();
          final Component component = content.getComponent();
          if (component instanceof MethodHierarchyBrowser) {
            ((MethodHierarchyBrowser)component).dispose();
            content.release();
          }
        }
      });
    }
    contentManager.setSelectedContent(content);
    hierarchyBrowser.setContent(content);

    final String name = method.getName();
    content.setDisplayName(name);

    final Runnable runnable = new Runnable() {
      public void run(){
        final String typeName = MethodHierarchyTreeStructure.TYPE;
        hierarchyBrowser.changeView(typeName);
      }
    };
    ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.HIERARCHY).activate(runnable);
  }

  public final void update(final AnActionEvent event){
    final Presentation presentation = event.getPresentation();
    if (!ActionPlaces.MAIN_MENU.equals(event.getPlace())) {
      presentation.setText(IdeBundle.message("action.browse.method.hierarchy"));
    }
    presentation.setEnabled(getMethod(event.getDataContext()) != null);
  }

  private static PsiMethod getMethod(final DataContext dataContext){
    final PsiMethod method = getMethodImpl(dataContext);
    if (
      method != null &&
      method.getContainingClass() != null &&
      !method.hasModifierProperty(PsiModifier.PRIVATE) &&
      !method.hasModifierProperty(PsiModifier.STATIC)
    ){
      return method;
    }
    else {
      return null;
    }
  }

  private static PsiMethod getMethodImpl(final DataContext dataContext){
    final Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;

    PsiElement element = DataKeys.PSI_ELEMENT.getData(dataContext);
    final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);

    if (method != null) {
      return method;
    }

    final Editor editor = DataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      return null;
    }

    final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile == null) {
      return null;
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final int offset = editor.getCaretModel().getOffset();
    if (offset < 1) {
      return null;
    }

    element = psiFile.findElementAt(offset);
    if (!(element instanceof PsiWhiteSpace)) {
      return null;
    }

    element = psiFile.findElementAt(offset - 1);
    if (!(element instanceof PsiJavaToken) || ((PsiJavaToken)element).getTokenType() != JavaTokenType.SEMICOLON) {
      return null;
    }

    return PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
  }
}