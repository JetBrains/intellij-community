package com.jetbrains.python.actions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInspection.HintAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyQualifiedExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles cases when an unresolved name may be imported from one of existing imported modules.
 * The object contains a list of import candidates and serves only to show the initial hint;
 * the actual work is done in ImportFromExistingAction..
 * User: dcheryasov
 * Date: Apr 15, 2009 2:06:25 PM
 */
public class ImportFromExistingFix implements HintAction {

  PyElement myNode;

  List<ImportCandidateHolder> myImports; // from where and what to import
  String myName;

  /**
   * Creates a new, empty fix object.
   * @param node to which the fix applies.
   * @param name the unresolved identifier portion of node's text
   */
  public ImportFromExistingFix(PyElement node, String name) {
    myNode = node;
    myImports = new ArrayList<ImportCandidateHolder>();
    myName = name;
  }

  /**
   * Adds another import source.
   * @param importable an element that could be imported either from import element or from file.
   * @param file the file which is the source of the importable
   * @param importElement an existing import element that can be a source for the importable.
   */
  public void addImport(@NotNull PsiElement importable, @NotNull PsiFile file, @Nullable PyImportElement importElement) {
    myImports.add(new ImportCandidateHolder(importable, file, importElement, null, null));
  }

  /**
   * Adds another import source.
   * @param importable an element that could be imported either from import element or from file.
   * @param file the file which is the source of the importable
   * @param importElement an existing import element that can be a source for the importable.
   * @param path import path for the file, as a qualified name (a.b.c)
   * @param asName name to use to import the path as: "import path as asName"
   */
  public void addImport(
    @NotNull PsiElement importable, @NotNull PsiFile file,
    @Nullable PyImportElement importElement, @Nullable String path, @Nullable String asName
  ) {
    myImports.add(new ImportCandidateHolder(importable, file, importElement, path, asName));
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
    if ((myNode instanceof PyQualifiedExpression) && ((((PyQualifiedExpression)myNode).getQualifier() != null))) return false; // we cannot be qualified
    final String message = ShowAutoImportPass.getMessage(
      myImports.size() > 1, 
      ImportCandidateHolder.getQualifiedName(myName, myImports.get(0).getPath(), myImports.get(0).getImportElement())
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
