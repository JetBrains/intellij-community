package com.intellij.ide.actions;

import com.intellij.execution.Location;
import com.intellij.ide.SelectInManager;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.IJSwingUtilities;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;

abstract class SelectInContext {
  private final PopupLocation myPopupLocation;
  private final PsiFile myPsiFile;

  protected SelectInContext(PopupLocation popupLocation, PsiFile psiFile) {
    myPopupLocation = popupLocation;
    myPsiFile = psiFile;
  }

  public SelectInManager getManager() { return SelectInManager.getInstance(getProject()); }
  public Project getProject() { return myPsiFile.getProject(); }
  public SelectInTarget[] getTargets() { return getTargets(myPsiFile); }
  public Point getPoint() { return myPopupLocation.getPoint(); }
  protected PsiFile getPsiFile() { return myPsiFile; }

  public abstract void selectIn(SelectInTarget selected);

  public static SelectInContext createContext(AnActionEvent event) {
    DataContext dataContext = event.getDataContext();
    SelectInContext result = createEditorContext(dataContext);
    if (result != null) return result;

    JComponent sourceComponent = getEventComponent(event);
    if (sourceComponent == null) return null;
    ComponentCenterLocation popupLocation = new ComponentCenterLocation(sourceComponent);

    SelectInContext psiContext = createPsiContext(event, popupLocation);
    if (psiContext != null) return psiContext;

    Navigatable descriptor = (Navigatable)dataContext.getData(DataConstants.NAVIGATABLE);
    if (!(descriptor instanceof OpenFileDescriptor)) return null;

    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    return OpenFileDescriptorContext.create(project, popupLocation, (OpenFileDescriptor)descriptor);
  }

  private static SelectInContext createEditorContext(DataContext dataContext) {
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    final FileEditor editor = (FileEditor)dataContext.getData(DataConstants.FILE_EDITOR);
    if (project == null || editor == null) return null;
    VirtualFile file = FileEditorManagerEx.getInstanceEx(project).getFile(editor);
    if (file == null) return null;
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile == null) return null;
    if (editor instanceof TextEditor) {
      return new TextEditorContext((TextEditor)editor, psiFile);
    }
    else {
      return new SimpleContext(new ComponentCenterLocation(editor.getComponent()), psiFile);
    }
  }

  private static SelectInContext createPsiContext(AnActionEvent event, PopupLocation popupLocation) {
    final DataContext dataContext = event.getDataContext();
    PsiElement psiElement = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
    if (psiElement == null || !psiElement.isValid()) {
      return null;
    }
    PsiFile psiFile = psiElement.getContainingFile();
    if (psiFile == null) {
      return null;
    }
    return new SimpleContext(popupLocation, psiFile, psiElement);
  }

  private static JComponent getEventComponent(AnActionEvent event) {
    InputEvent inputEvent = event.getInputEvent();
    Object source;
    if (inputEvent != null && (source = inputEvent.getSource()) instanceof JComponent) {
      return (JComponent)source;
    }
    else {
      return Location.safeCast(event.getDataContext().getData(DataConstantsEx.CONTEXT_COMPONENT), JComponent.class);
    }
  }

  protected SelectInTarget[] getTargets(PsiFile psiFile) {
    ArrayList<SelectInTarget> result = new ArrayList<SelectInTarget>();
    final SelectInTarget[] targets = getManager().getTargets();
    for (int i = 0; i < targets.length; i++) {
      SelectInTarget target = targets[i];
      if (target.canSelect(psiFile)) {
        result.add(target);
      }
    }
    if (result.size() > 1) {
      final String activeToolWindowId = ToolWindowManager.getInstance(getProject()).getActiveToolWindowId();
      if (activeToolWindowId != null) {
        SelectInTarget firstTarget = result.get(0);
        if (activeToolWindowId.equals(firstTarget.getToolWindowId())) {
          boolean shouldMoveToBottom = true;
          if (ToolWindowId.PROJECT_VIEW.equals(activeToolWindowId)) {
            final String currentMinorViewId = ProjectView.getInstance(getProject()).getCurrentViewId();
            shouldMoveToBottom = (currentMinorViewId != null) && currentMinorViewId.equals(firstTarget.getMinorViewId());
          }
          if (shouldMoveToBottom) {
            result.remove(0);
            result.add(firstTarget);
          }
        }
      }
    }
    return result.toArray(new SelectInTarget[result.size()]);
  }

  private interface PopupLocation {
    Point getPoint();
  }

  private static class ComponentCenterLocation implements PopupLocation {
    private final JComponent myComponent;

    public ComponentCenterLocation(JComponent component) {
      myComponent = component;
    }

    public Point getPoint() {

      JViewport viewport = IJSwingUtilities.findParentOfType(myComponent, JViewport.class);
      JComponent component;
      if (viewport != null) {
        component = viewport;
      }
      else {
        component = myComponent;
      }
      Point p = new Point(component.getWidth() / 2, component.getHeight() / 2);
      SwingUtilities.convertPointToScreen(p, component);
      return p;
    }
  }

  private static class EditorCaretLocation implements PopupLocation {
    private final Editor editor;

    public EditorCaretLocation(Editor editor) {
      this.editor = editor;
    }

    public Point getPoint() {
      Point p;
      Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
      p = editor.logicalPositionToXY(editor.getCaretModel().getLogicalPosition());
      if (!visibleArea.contains(p)) p = visibleArea.getLocation();
      SwingUtilities.convertPointToScreen(p, editor.getContentComponent());
      return p;
    }
  }

  private static class TextEditorContext extends SelectInContext {
    private final TextEditor myEditor;

    public TextEditorContext(TextEditor editor, PsiFile psiFile) {
      super(new EditorCaretLocation(editor.getEditor()), psiFile);
      myEditor = editor;
    }

    public void selectIn(final SelectInTarget selected) {
      CommandProcessor commandProcessor = CommandProcessor.getInstance();
      Runnable runnable = new Runnable() {
        public void run() {
          PsiElement element = getPsiFile();
          Editor editor = myEditor.getEditor();
          final int offset = editor.getCaretModel().getOffset();
          PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
          PsiElement e = getPsiFile().findElementAt(offset);
          if (e != null) {
            element = e;
          }
          selected.select(element, true);
        }
      };
      commandProcessor.executeCommand(getProject(), runnable, "Select in " + selected, null);
    }
  }

  private static class OpenFileDescriptorContext extends SelectInContext {
    private final OpenFileDescriptor myDescriptor;

    public OpenFileDescriptorContext(PopupLocation popupLocation, PsiFile psiFile, OpenFileDescriptor descriptor) {
      super(popupLocation, psiFile);
      myDescriptor = descriptor;
    }

    public void selectIn(SelectInTarget selected) {
      PsiElement psiElement;
      if (myDescriptor.getOffset() >= 0) psiElement = findElementAt(myDescriptor.getOffset());
      else {
        String text = LoadTextUtil.loadText(myDescriptor.getFile(), new String[1]).toString();
        psiElement = findElementAt(StringUtil.lineColToOffset(text, myDescriptor.getLine(), myDescriptor.getColumn()));
      }
      selected.select(psiElement, true);
    }

    private PsiElement findElementAt(int offset) {
      PsiElement psiElement = getPsiFile().findElementAt(offset);
      return psiElement != null ? psiElement : getPsiFile();
    }

    public static SelectInContext create(Project project, PopupLocation popupLocation, OpenFileDescriptor descriptor) {
      if (descriptor == null) return null;
      VirtualFile file = descriptor.getFile();
      if (file == null || !file.isValid()) return null;
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile == null) return null;
      return new OpenFileDescriptorContext(popupLocation, psiFile, descriptor);
    }
  }

  private static class SimpleContext extends SelectInContext {
    private final PsiElement myElementToSelect;

    public SimpleContext(PopupLocation popupLocation, PsiFile psiFile) {
      this(popupLocation, psiFile, psiFile);
    }

    public SimpleContext(PopupLocation popupLocation, PsiFile psiFile, PsiElement elementToSelect) {
      super(popupLocation, psiFile);
      myElementToSelect = elementToSelect;
    }

    public void selectIn(SelectInTarget selected) {
      selected.select(myElementToSelect, true);
    }
  }
}
