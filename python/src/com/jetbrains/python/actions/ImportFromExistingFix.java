package com.jetbrains.python.actions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInspection.HintAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyImportElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles cases when an unresolved name may be imported from one of existing imported modules.
 * The object contains a list of modules from which given name might be imported.
 * User: dcheryasov
 * Date: Apr 15, 2009 2:06:25 PM
 */
public class ImportFromExistingFix implements HintAction {

  PyElement myNode;

  List<Pair<PyImportElement, PsiElement>> myImports; // from where and what to import
  String myName;

  /**
   * Creates a new, empty fix object.
   * @param node to which the fix applies.
   */
  public ImportFromExistingFix(PyElement node, String name) {
    myNode = node;
    myImports = new ArrayList<Pair<PyImportElement, PsiElement>>();
    myName = name;
  }

  /**
   * Creates a mew fix object with one import variant.
   * @param node to which the fix applies.
   * @param source from which the name is importable.
   * @param name
   */
  public ImportFromExistingFix(PyElement node, PyImportElement source, PsiElement item, String name) {
    this(node, name);
    addImport(source, item);
  }

  /**
   * Adds another import source.
   * @param source an import statement from which the name is importable.
   */
  public void addImport(PyImportElement source, PsiElement item) {
    myImports.add(new Pair<PyImportElement, PsiElement>(source, item));
  }

  @NotNull
  public String getText() {
    return PyBundle.message("ACT.NAME.use.import");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("ACT.FAMILY.import");
  }

  public boolean showHint(Editor editor) {
    if (myNode == null || !myNode.isValid() || myNode.getName() == null || myImports.size() <= 0) {
      return false; // TODO: also return false if an on-the-fly unambiguous fix is possible?
    }
    final String message = ShowAutoImportPass.getMessage(
      myImports.size() > 1, 
      myImports.get(0).getFirst().getVisibleName()+"."+myNode.getName()
    );
    final ImportFromExistingAction action = new ImportFromExistingAction(myNode, myImports, myName, editor);
    HintManager.getInstance().showQuestionHint(
      editor, message,
      myNode.getTextOffset(),
      myNode.getTextRange().getEndOffset(), action);
    return true;
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myNode != null && myNode.isValid() && myImports.size() > 0; 
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    // make sure file is committed, writable, etc
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    // act
    ImportFromExistingAction action = new ImportFromExistingAction(myNode, myImports, myName, editor);
    action.execute(); // assume that action runs in WriteAction on its own behalf
  }

  public boolean startInWriteAction() {
    return false; // multiple variants may make us show a menu
  }
}
