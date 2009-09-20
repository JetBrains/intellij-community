package com.jetbrains.python.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.awt.*;

/**
 * Turns an unqualified unresolved identifier into qualified and resolvable.
 * User: dcheryasov
 * Date: Apr 15, 2009 6:24:48 PM
 */
public class ImportFromExistingAction implements QuestionAction {
  PyElement myTarget;
  List<ImportCandidateHolder> mySources; // list of <import, imported_item>
  Editor myEditor;
  String myName;
  boolean myUseQualifiedImport;

  /**
   * @param target element to become qualified as imported.
   * @param sources clauses of import to be used.
   * @param name relevant name ot the target element (e.g. of identifier in an expression).
   * @param editor target's editor.
   */
  public ImportFromExistingAction(@NotNull PyElement target, @NotNull List<ImportCandidateHolder> sources, String name, Editor editor) {
    myTarget = target;
    mySources = sources;
    myName = name;
    myEditor = editor;
    myUseQualifiedImport = false;
  }

  /**
   * @param target element to become qualified as imported.
   * @param sources clauses of import to be used.
   * @param name relevant name ot the target element (e.g. of identifier in an expression).
   * @param editor target's editor.
   * @param useQualified if True, use qualified "import modulename" instead of "from modulename import ...".
   */
  public ImportFromExistingAction(@NotNull PyElement target, @NotNull List<ImportCandidateHolder> sources, String name, Editor editor, boolean useQualified) {
    myTarget = target;
    mySources = sources;
    myName = name;
    myEditor = editor;
    myUseQualifiedImport = useQualified;
  }




  /**
   * Alters either target (by qualifying a name) or source (by explicitly importing the name).
   * @return true if action succeeded
   */
  public boolean execute() {
    // check if the tree is sane
    PsiDocumentManager.getInstance(myTarget.getProject()).commitAllDocuments();
    if (!myTarget.isValid()) return false;
    if ((myTarget instanceof PyQualifiedExpression) && ((((PyQualifiedExpression)myTarget).getQualifier() != null))) return false; // we cannot be qualified
    for (ImportCandidateHolder item : mySources) {
      if (!item.getImportable().isValid()) return false;
      if (!item.getFile().isValid()) return false;
      if (item.getImportElement() != null && !item.getImportElement().isValid()) return false;
    }
    // act
    if (mySources.size() > 1) {
      selectSourceAndDo();
    }
    else doWriteAction(mySources.get(0));
    return true;
  }

  private void selectSourceAndDo() {
    // GUI part
    ImportCandidateHolder[] items = mySources.toArray(new ImportCandidateHolder[mySources.size()]); // silly JList can't handle modern collections
    final JList list = new JList(items);
    list.setCellRenderer(new CellRenderer(myName));

    Runnable runnable = new Runnable() {
      public void run() {
        int index = list.getSelectedIndex();
        if (index < 0) return;
        PsiDocumentManager.getInstance(myTarget.getProject()).commitAllDocuments();
        doWriteAction(mySources.get(index));
      }
    };

    new PopupChooserBuilder(list).
      setTitle(myUseQualifiedImport? PyBundle.message("ACT.qualify.with.module") : PyBundle.message("ACT.from.some.module.import")).
      setItemChoosenCallback(runnable).
      createPopup().
      showInBestPositionFor(myEditor)
    ;
  }

  private void doIt(final ImportCandidateHolder item) {
    PyImportElement src = item.getImportElement();
    final PyElementGenerator gen = PythonLanguage.getInstance().getElementGenerator();
    if (src != null) { // use existing import
      // did user choose 'import' or 'from import'?
      PsiElement parent = src.getParent();
      if (parent instanceof PyFromImportStatement) {
        // add another import element right after the one we got
        final Project project = myTarget.getProject();
        PsiElement new_elt = gen.createFromText(project, PyImportElement.class, "from foo import " + myName, new int[]{0, 6});
        PyUtil.addListNode(parent, new_elt, null, false, true);
      }
      else { // just 'import'
        // all we need is to qualify our target
        myTarget.replace(gen.createExpressionFromText(myTarget.getProject(), src.getVisibleName() + "." + myName));
      }
    }
    else { // no existing import, add it then use it
      Project project = myTarget.getProject();
      if (myUseQualifiedImport) {
        AddImportHelper.addImportStatement(myTarget.getContainingFile(), item.getPath(), null, project);
        String qual_name;
        if (item.getAsName() != null) qual_name = item.getAsName();
        else qual_name = item.getPath();
        myTarget.replace(gen.createExpressionFromText(project, qual_name + "." + myName));
      }
      else {
        AddImportHelper.addImportFromStatement(myTarget.getContainingFile(), item.getPath(), myName, null, project);
      }
    }
  }

  private void doWriteAction(final ImportCandidateHolder item) {
    PsiElement src = item.getImportable();
    CommandProcessor.getInstance().executeCommand(src.getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            doIt(item);
          }
        });
      }
    }, PyBundle.message("ACT.CMD.use.import"), null);
  }

  // Stolen from FQNameCellRenderer
  private static class CellRenderer extends SimpleColoredComponent implements ListCellRenderer {
    private final Font FONT;
    private final String myName;

    public CellRenderer(String name) {
      myName = name;
      EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
      FONT = new Font(scheme.getEditorFontName(), Font.PLAIN, scheme.getEditorFontSize());
      setOpaque(true);
    }

    // value is a QualifiedHolder
    public Component getListCellRendererComponent(
      JList list,
      Object value, // expected to be
      int index,
      boolean isSelected,
      boolean cellHasFocus
    ){

      clear();

      ImportCandidateHolder item = (ImportCandidateHolder)value;
      setIcon(item.getImportable().getIcon(0));
      String item_name = item.getPresentableText(myName);
      append(item_name, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      setFont(FONT);
      if (isSelected) {
        setBackground(list.getSelectionBackground());
        setForeground(list.getSelectionForeground());
      }
      else {
        setBackground(list.getBackground());
        setForeground(list.getForeground());
      }
      return this;
    }
  }
}
