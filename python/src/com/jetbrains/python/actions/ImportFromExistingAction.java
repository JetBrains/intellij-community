package com.jetbrains.python.actions;

import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Turns an unqualified unresolved identifier into qualifed and resolvable.
 * User: dcheryasov
 * Date: Apr 15, 2009 6:24:48 PM
 */
public class ImportFromExistingAction implements QuestionAction {

  PyElement myTarget;
  List<Pair<PyImportElement, PsiElement>> mySources; // list of <import, imported_item>
  Editor myEditor;
  String myName;

  /**
   * @param target element to become qualified as imported.
   * @param sources clauses of import to be used.
   */
  public ImportFromExistingAction(@NotNull PyElement target, @NotNull List<Pair<PyImportElement, PsiElement>> sources, String name, Editor editor) {
    mySources = sources;
    myTarget = target;
    myEditor = editor;
    myName = name;
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
    for (Pair<PyImportElement, PsiElement> src : mySources) {
      if (!src.getFirst().isValid()) return false;
      if (!src.getSecond().isValid()) return false;
    }
    // act
    if (mySources.size() > 1) {
      selectSourceAndDo();
    }
    else doWriteAction(mySources.get(0).getFirst());
    return true; 
  }

  private void selectSourceAndDo() {
    // GUI part
    QualifiedHolder[] items = new QualifiedHolder[mySources.size()];
    int i = 0;
    for (Pair<PyImportElement, PsiElement> pair : mySources) {
      items[i] = new QualifiedHolder(pair.getFirst(), pair.getSecond(), myName);
      i += 1;
    }
    final JList list = new JList(items);
    list.setCellRenderer(new CellRenderer());
    
    Runnable runnable = new Runnable() {
      public void run() {
        int index = list.getSelectedIndex();
        if (index < 0) return;
        PsiDocumentManager.getInstance(myTarget.getProject()).commitAllDocuments();
        doWriteAction(mySources.get(index).getFirst());
      }
    };

    new PopupChooserBuilder(list).
      setTitle(PyBundle.message("ACT.qualify.with.module")).
      setItemChoosenCallback(runnable).
      createPopup().
      showInBestPositionFor(myEditor)
    ;
  }

  private void doIt(final PyImportElement src) {
    // did user choose 'import' or 'from import'?
    PsiElement parent = src.getParent();
    if (parent instanceof PyFromImportStatement) {
      // add another import element right after the one we got
      final PyElementGenerator gen = PythonLanguage.getInstance().getElementGenerator();
      final Project project = myTarget.getProject();
      PsiElement new_elt = gen.
        createFromText(project, PyImportElement.class, "from foo import " + myName, new int[]{0,6})
      ;
      PyUtil.addListNode(parent, new_elt, null, false, true);
    }
    else { // just 'import'
      // all we need is to qualify our target
      myTarget.replace(
        PythonLanguage.getInstance().
        getElementGenerator().
        createExpressionFromText(myTarget.getProject(), src.getVisibleName()+ "." + myName)
      );
    }
  }

  private void doWriteAction(final PyImportElement src) {
    CommandProcessor.getInstance().executeCommand(src.getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            doIt(src);
          }
        });
      }
    }, PyBundle.message("ACT.CMD.use.import"), null);
  }


  // items to store in list
  private static class QualifiedHolder {
    final PyImportElement mySrc;
    final PsiElement myItem;
    final String myName;

    public QualifiedHolder(PyImportElement src, PsiElement item, String name) {
      mySrc = src;
      myItem = item;
      myName = name;
    }

    public Icon getIcon() {
      return myItem.getIcon(0);
    }

    @Override
    public String toString() {
      StringBuffer sb = new StringBuffer();
      PsiElement parent = mySrc.getParent();
      if (parent instanceof PyFromImportStatement) {
        sb.append(myName);
      }
      else {
        sb.append(mySrc.getVisibleName()).append(".").append(myName);
      }
      if (myItem instanceof PyFunction) {
        sb.append("(");
        // below: ", ".join([x.getRepr(False) for x in getParameters()])
        PyParameter[] params = ((PyFunction)myItem).getParameterList().getParameters();
        String[] param_reprs = new String[params.length];
        for (int i=0; i < params.length; i += 1) param_reprs[i] = params[i].getRepr(false);
        PyUtil.joinSubarray(param_reprs, 0, params.length, ", ", sb);
        sb.append(")");
      }
      else if (myItem instanceof PyClass) {
        PyClass[] supers = ((PyClass)myItem).getSuperClasses();
        if (supers.length > 0) {
          sb.append("(");
          // ", ".join(x.getName() for x in getSuperClasses())
          String[] super_names = new String[supers.length];
          for (int i=0; i < supers.length; i += 1) super_names[i] = supers[i].getName();
          PyUtil.joinSubarray(super_names, 0, supers.length, ", ", sb);
          sb.append(")");
        }
      }
      if (parent instanceof PyFromImportStatement) {
        sb.append(" from ").append(((PyFromImportStatement)parent).getImportSource().getReferencedName());
      }
      return sb.toString();
    }
  }

  // Stolen from FQNameCellRenderer
  private static class CellRenderer extends SimpleColoredComponent implements ListCellRenderer {
    private final Font FONT;

    public CellRenderer() {
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

      QualifiedHolder item = (QualifiedHolder)value;
      setIcon(item.getIcon());
      String item_name = item.toString();
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
