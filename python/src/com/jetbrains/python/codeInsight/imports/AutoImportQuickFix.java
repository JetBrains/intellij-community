package com.jetbrains.python.codeInsight.imports;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The object contains a list of import candidates and serves only to show the initial hint;
 * the actual work is done in ImportFromExistingAction..
 *
 * @author dcheryasov
 */
public class AutoImportQuickFix implements LocalQuickFix {

  private final PyElement myNode;
  private final PsiReference myReference;

  private final List<ImportCandidateHolder> myImports; // from where and what to import
  String myName;

  boolean myUseQualifiedImport;
  private boolean myExpended;

  /**
   * Creates a new, empty fix object.
   * @param node to which the fix applies.
   * @param name the unresolved identifier portion of node's text
   * @param qualify if true, add an "import ..." statement and qualify the name; else use "from ... import name" 
   */
  public AutoImportQuickFix(PyElement node, PsiReference reference, String name, boolean qualify) {
    myNode = node;
    myReference = reference;
    myImports = new ArrayList<ImportCandidateHolder>();
    myName = name;
    myUseQualifiedImport = qualify;
    myExpended = false;
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
   * @param path import path for the file, as a qualified name (a.b.c)
   * @param asName name to use to import the path as: "import path as asName"
   */
  public void addImport(@NotNull PsiElement importable, @NotNull PsiFileSystemItem file, @Nullable PyQualifiedName path, @Nullable String asName) {
    myImports.add(new ImportCandidateHolder(importable, file, null, path, asName));
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
    if (!PyCodeInsightSettings.getInstance().SHOW_IMPORT_POPUP) {
      return false;
    }
    if (myNode == null || !myNode.isValid() || myNode.getName() == null || myImports.size() <= 0) {
      return false; // TODO: also return false if an on-the-fly unambiguous fix is possible?
    }
    if (ImportFromExistingAction.isResolved(myReference)) {
      return false;
    }
    if ((myNode instanceof PyQualifiedExpression) && ((((PyQualifiedExpression)myNode).getQualifier() != null))) return false; // we cannot be qualified
    final String message = ShowAutoImportPass.getMessage(
      myImports.size() > 1,
      ImportCandidateHolder.getQualifiedName(myName, myImports.get(0).getPath(), myImports.get(0).getImportElement())
    );
    final ImportFromExistingAction action = new ImportFromExistingAction(myNode, myImports, myName, myUseQualifiedImport);
    action.onDone(new Runnable() {
      public void run() {
        myExpended = true;
      }
    });
    HintManager.getInstance().showQuestionHint(
      editor, message,
      myNode.getTextOffset(),
      myNode.getTextRange().getEndOffset(), action);
    return true;
  }

  public boolean isAvailable() {
    return !myExpended && myNode != null && myNode.isValid() && myImports.size() > 0;
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    invoke(descriptor.getPsiElement().getContainingFile());
    myExpended = true;
  }

  public void invoke(PsiFile file) throws IncorrectOperationException {
    // make sure file is committed, writable, etc
    if (!myReference.getElement().isValid()) return;
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    if (ImportFromExistingAction.isResolved(myReference)) return;
    // act
    ImportFromExistingAction action = new ImportFromExistingAction(myNode, myImports, myName, myUseQualifiedImport);
    action.execute(); // assume that action runs in WriteAction on its own behalf
    myExpended = true;
  }

  public void sortCandidates() {
    Collections.sort(myImports);
  }

  public int getCandidatesCount() {
    return myImports.size();
  }

  public boolean hasOnlyFunctions() {
    for (ImportCandidateHolder holder : myImports) {
      if (!(holder.getImportable() instanceof PyFunction)) {
        return false;
      }
    }
    return true;
  }
}
