package com.intellij.ide.actions;

import com.intellij.execution.Location;
import com.intellij.ide.SelectInContext;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.IJSwingUtilities;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;

abstract class SelectInContextImpl implements SelectInContext {
  private final PsiFile myPsiFile;

  protected SelectInContextImpl(PsiFile psiFile) {
    myPsiFile = psiFile;
  }

  public Project getProject() { return myPsiFile.getProject(); }

  public PsiFile getPsiFile() { return myPsiFile; }
  public VirtualFile getVirtualFile() { return myPsiFile.getVirtualFile(); }

  public Object getSelectorInFile() {
    return getPsiElement();
  }

  public static SelectInContextProvider createContext(AnActionEvent event) {
    DataContext dataContext = event.getDataContext();

    SelectInContextProvider result = createEditorContext(dataContext);
    if (result != null) return result;

    JComponent sourceComponent = getEventComponent(event);
    if (sourceComponent == null) return null;
    ComponentCenterLocation popupLocation = new ComponentCenterLocation(sourceComponent);

    SelectInContext selectInContext = ((SelectInContext)dataContext.getData(SelectInContext.DATA_CONTEXT_ID));
    if (selectInContext == null) {
      selectInContext = createPsiContext(event);
    }
    if (selectInContext == null) {
      Navigatable descriptor = (Navigatable)dataContext.getData(DataConstants.NAVIGATABLE);
      if (!(descriptor instanceof OpenFileDescriptor)) return null;

      Project project = (Project)dataContext.getData(DataConstants.PROJECT);
      selectInContext = OpenFileDescriptorContext.create(project, (OpenFileDescriptor)descriptor);
    }

    return new SelectInContextProvider(selectInContext, popupLocation);
  }

  private static SelectInContextProvider createEditorContext(DataContext dataContext) {
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    final FileEditor editor = (FileEditor)dataContext.getData(DataConstants.FILE_EDITOR);
    if (project == null || editor == null) return null;
    VirtualFile file = FileEditorManagerEx.getInstanceEx(project).getFile(editor);
    if (file == null) return null;
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile == null) return null;

    if (editor instanceof TextEditor) {
      return new SelectInContextProvider(
        new TextEditorContext((TextEditor)editor, psiFile),
        new EditorCaretLocation(((TextEditor)editor).getEditor())
      );
    }
    else {
      return new SelectInContextProvider(
        new SimpleSelectInContext(psiFile),
        new ComponentCenterLocation(editor.getComponent())
      );
    }
  }

  private static SelectInContext createPsiContext(AnActionEvent event) {
    final DataContext dataContext = event.getDataContext();
    PsiElement psiElement = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
    if (psiElement == null || !psiElement.isValid()) {
      return null;
    }
    PsiFile psiFile = psiElement.getContainingFile();
    if (psiFile == null) {
      return null;
    }
    return new SimpleSelectInContext(psiFile, psiElement);
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

  static class SelectInContextProvider {
    private final SelectInContext myContext;
    private final PopupLocation myPopupLocation;

    public SelectInContextProvider(SelectInContext context, PopupLocation popupLocation) {
      assert context != null : "Null SelectInContext";
      assert popupLocation != null : "Null PopupLocation";

      myContext = context;
      myPopupLocation = popupLocation;
    }

    public SelectInContext getContext() { return myContext; }
    public Point getInvocationPoint() { return myPopupLocation.getPoint(); }
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

  private static class TextEditorContext extends SelectInContextImpl {
    private final TextEditor myEditor;

    public TextEditorContext(TextEditor editor, PsiFile psiFile) {
      super(psiFile);
      myEditor = editor;
    }

    public PsiElement getPsiElement() {
      PsiElement element = getPsiFile();
      Editor editor = myEditor.getEditor();
      final int offset = editor.getCaretModel().getOffset();
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      PsiElement e = getPsiFile().findElementAt(offset);
      if (e != null) {
        element = e;
      }
      return element;
    }
  }

  private static class OpenFileDescriptorContext extends SelectInContextImpl {
    private final OpenFileDescriptor myDescriptor;

    public OpenFileDescriptorContext(PsiFile psiFile, OpenFileDescriptor descriptor) {
      super(psiFile);
      myDescriptor = descriptor;
    }

    public PsiElement getPsiElement() {
      PsiElement psiElement;
      if (myDescriptor.getOffset() >= 0) psiElement = findElementAt(myDescriptor.getOffset());
      else {
        String text = LoadTextUtil.loadText(myDescriptor.getFile(), new String[1]).toString();
        psiElement = findElementAt(StringUtil.lineColToOffset(text, myDescriptor.getLine(), myDescriptor.getColumn()));
      }
      return psiElement;
    }

    private PsiElement findElementAt(int offset) {
      PsiElement psiElement = getPsiFile().findElementAt(offset);
      return psiElement != null ? psiElement : getPsiFile();
    }

    public static SelectInContext create(Project project, OpenFileDescriptor descriptor) {
      if (descriptor == null) return null;
      VirtualFile file = descriptor.getFile();
      if (file == null || !file.isValid()) return null;
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile == null) return null;
      return new OpenFileDescriptorContext(psiFile, descriptor);
    }
  }

  private static class SimpleSelectInContext extends SelectInContextImpl {
    private final PsiElement myElementToSelect;

    public SimpleSelectInContext(PsiFile psiFile) {
      this(psiFile, psiFile);
    }

    public SimpleSelectInContext(PsiFile psiFile, PsiElement elementToSelect) {
      super(psiFile);
      myElementToSelect = elementToSelect;
    }

    public PsiElement getPsiElement() {
      return myElementToSelect;
    }
  }
}
