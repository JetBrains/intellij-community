/**
 * @author Alexey
 */
package com.intellij.ide.actions;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.ide.CopyPasteManagerEx;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

public class CopyReferenceAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.CopyReferenceAction");

  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    boolean enabled = editor != null;
    e.getPresentation().setEnabled(enabled);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());

    if (!(element instanceof PsiIdentifier)) return;
    final PsiElement parent = element.getParent();
    PsiMember member = null;
    if (parent instanceof PsiReferenceExpression) {
      PsiElement resolved = ((PsiReferenceExpression)parent).resolve();
      if (resolved instanceof PsiMember) {
        member = (PsiMember)resolved;
      }
    }
    else if (parent instanceof PsiMember) {
      member = (PsiMember)parent;
    }
    else {
      //todo show error
      return;
    }

    if (member != null) {
      doCopy(member, project);

      HighlightManager highlightManager = HighlightManager.getInstance(project);
      EditorColorsManager manager = EditorColorsManager.getInstance();
      TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{element}, attributes, true, null);
    }
  }

  public static void doCopy(final PsiElement element, final Project project) {
    String fqn = elementToFqn(element);

    CopyPasteManagerEx.getInstance().setContents(new MyTransferable(fqn));

    final StatusBarEx statusBar = (StatusBarEx)WindowManager.getInstance().getStatusBar(project);
    statusBar.setInfo("Reference to '"+fqn+"' has been copied.");
  }

  private static void insert(final String fqn, final PsiNamedElement element, final Editor editor) {
    final PsiFile file = PsiDocumentManager.getInstance(editor.getProject()).getPsiFile(editor.getDocument());
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;

    final Project project = editor.getProject();
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              doInsert(fqn, element, editor, project);

            }
            catch (IncorrectOperationException e1) {
              LOG.error(e1);
            }
          }
        });
      }
    }, "Pasting reference", null);

    final StatusBarEx statusBar = (StatusBarEx)WindowManager.getInstance().getStatusBar(project);
    statusBar.setInfo("Reference to '"+fqn+"' has been pasted.");
  }

  private static void doInsert(String fqn,
                               PsiNamedElement element,
                               final Editor editor,
                               final Project project) throws IncorrectOperationException {
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document = editor.getDocument();
    final PsiFile file = documentManager.getPsiFile(document);

    final int offset = editor.getCaretModel().getOffset();
    fqn = fqn.replace('#', '.');
    String toInsert = element.getName();
    String suffix = "";
    if (element instanceof PsiClass) {
        suffix = " ";
      }
    else if (element instanceof PsiMethod) {
      suffix = "()";
    }
    final PsiElementFactory factory = PsiManager.getInstance(project).getElementFactory();
    final PsiExpression expression = factory.createExpressionFromText(toInsert+suffix, file.findElementAt(offset));
    final PsiReferenceExpression referenceExpression=expression instanceof PsiMethodCallExpression ?
      ((PsiMethodCallExpression)expression).getMethodExpression() :  expression instanceof PsiReferenceExpression ?
      (PsiReferenceExpression)expression : null;
    if (referenceExpression == null || referenceExpression.advancedResolve(true).getElement() != element) {
      toInsert = fqn;
    }

    document.insertString(offset, toInsert+suffix);
    documentManager.commitDocument(document);
    int endOffset = offset + toInsert.length() + suffix.length();
    RangeMarker rangeMarker = document.createRangeMarker(endOffset, endOffset);
    PsiElement elementAt = file.findElementAt(offset);

    shortenReference(elementAt);
    CodeStyleManager.getInstance(project).adjustLineIndent(file, offset);

    int caretOffset = rangeMarker.getEndOffset();
    if (element instanceof PsiMethod) {
      caretOffset --;
    }
    editor.getCaretModel().moveToOffset(caretOffset);
  }

  private static void shortenReference(PsiElement element) throws IncorrectOperationException {
    if (element == null) return;
    while (element.getParent() instanceof PsiJavaCodeReferenceElement) {
      element = element.getParent();
    }

    final CodeStyleManagerEx codeStyleManagerEx = (CodeStyleManagerEx)CodeStyleManager.getInstance(element.getManager().getProject());
    codeStyleManagerEx.shortenClassReferences(element, CodeStyleManagerEx.UNCOMPLETE_CODE);
  }

  public static class MyPasteProvider implements PasteProvider {
    public void performPaste(DataContext dataContext) {
      final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
      final Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
      if (project == null || editor == null) return;

      final String fqn = getCopiedFqn();
      PsiMember member = CopyReferenceAction.fqnToMember(project, fqn);
      insert(fqn, (PsiNamedElement)member, editor);

      if (editor.getSelectionModel().hasSelection()) {
        ApplicationManager.getApplication().runWriteAction(
          new Runnable() {
            public void run() {
              EditorModificationUtil.deleteSelectedText(editor);
            }
          }
        );
      }
    }

    public boolean isPastePossible(DataContext dataContext) {
      return isPasteEnabled(dataContext);
    }

    public boolean isPasteEnabled(DataContext dataContext) {
      final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
      final Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
      if (project == null || editor == null) return false;
      return getCopiedFqn() != null;
    }
  }

  private static String getCopiedFqn() {
    final Transferable contents = CopyPasteManagerEx.getInstance().getContents();
    if (contents == null) return null;
    try {
      final String fqn = (String)contents.getTransferData(OUR_DATA_FLAVOR);
      return fqn;
    }
    catch (UnsupportedFlavorException e) {
    }
    catch (IOException e) {
    }
    return null;
  }

  private static final DataFlavor OUR_DATA_FLAVOR;
  static {
    try {
      OUR_DATA_FLAVOR = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + MyTransferable.class.getName());
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private static class MyTransferable implements Transferable {
    private final String fqn;

    public MyTransferable(String fqn) {
      this.fqn = fqn;
    }

    public DataFlavor[] getTransferDataFlavors() {
      return new DataFlavor[]{OUR_DATA_FLAVOR};
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
      return OUR_DATA_FLAVOR.equals(flavor);
    }

    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
      if (!isDataFlavorSupported(flavor)) return null;
      return fqn;
    }
  }

  public static String elementToFqn(final PsiElement element) {
    final String fqn;
    if (element instanceof PsiClass) {
      fqn = ((PsiClass)element).getQualifiedName();
    }
    else if (element instanceof PsiMember) {
      final PsiMember member = (PsiMember)element;
      fqn = member.getContainingClass().getQualifiedName() + "#" + member.getName();
    }
    else if (element instanceof PsiFile) {
      final PsiFile file = (PsiFile)element;
      final VirtualFile virtualFile = file.getVirtualFile();
      fqn = virtualFile == null ? file.getName() : FileUtil.toSystemDependentName(virtualFile.getPath());
    }
    else {
      fqn = element.getClass().getName();
    }
    return fqn;
  }

  static PsiMember fqnToMember(final Project project, final String fqn) {
    PsiClass aClass = PsiManager.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));
    PsiMember member;
    if (aClass != null) {
      member = aClass;
    }
    else {
      String className = fqn.substring(0, fqn.indexOf("#"));
      aClass = PsiManager.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
      String memberName = fqn.substring(fqn.indexOf("#") + 1);
      member = aClass.findFieldByName(memberName, false);
      if (member == null) {
        member = aClass.findMethodsByName(memberName, false)[0];
      }
    }
    return member;
  }
}
