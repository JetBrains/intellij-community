package com.jetbrains.python.actions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInspection.HintAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
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
 */
public class ImportFromExistingFix implements HintAction, LocalQuickFix {

  PyElement myNode;

  List<ImportCandidateHolder> myImports; // from where and what to import
  String myName;

  boolean myUseQualifiedImport;
  private boolean myExpended;

  /**
   * Creates a new, empty fix object.
   * @param node to which the fix applies.
   * @param name the unresolved identifier portion of node's text
   * @param qualify if true, add an "import ..." statement and qualify the name; else use "from ... import name" 
   */
  public ImportFromExistingFix(PyElement node, String name, boolean qualify) {
    myNode = node;
    myImports = new ArrayList<ImportCandidateHolder>();
    myName = name;
    myUseQualifiedImport = qualify;
    myExpended = false;
  }

  /**
   * @return a fix that uses all the same data, but with 'qualify' parameter inverted.
   */
  public ImportFromExistingFix createDual() {
    ImportFromExistingFix piggybacked = new ImportFromExistingFix(myNode, myName, ! myUseQualifiedImport);
    piggybacked.myImports = myImports;
    return piggybacked;
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
    if (myUseQualifiedImport) return PyBundle.message("ACT.qualify.with.module");
    else return PyBundle.message("ACT.NAME.use.import");
  }

  @NotNull
  public String getName() {
    return getText();
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
    final ImportFromExistingAction action = new ImportFromExistingAction(myNode, myImports, myName, editor, myUseQualifiedImport);
    HintManager.getInstance().showQuestionHint(
      editor, message,
      myNode.getTextOffset(),
      myNode.getTextRange().getEndOffset(), action);
    return true;
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return !myExpended && myNode != null && myNode.isValid() && myImports.size() > 0;
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    Editor editor = (Editor)DataManager.getInstance().getDataContext().getData(DataConstants.EDITOR);
    if (editor != null) {
      invoke(project, editor, descriptor.getPsiElement().getContainingFile());
    }
    myExpended = true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    // make sure file is committed, writable, etc
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    // act
    ImportFromExistingAction action = new ImportFromExistingAction(myNode, myImports, myName, editor, myUseQualifiedImport);
    action.execute(); // assume that action runs in WriteAction on its own behalf
    myExpended = true;
  }

  public boolean startInWriteAction() {
    return false; // multiple variants may make us show a menu
  }
}
