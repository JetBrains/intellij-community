// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.imports;

import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * Turns an unqualified unresolved identifier into qualified and resolvable.
 *
 * @author dcheryasov
 */
public class ImportFromExistingAction implements QuestionAction {
  PsiElement myTarget;
  List<? extends ImportCandidateHolder> mySources; // list of <import, imported_item>
  String myName;
  boolean myUseQualifiedImport;
  private Runnable myOnDoneCallback;
  private final boolean myImportLocally;

  /**
   * @param target element to become qualified as imported.
   * @param sources clauses of import to be used.
   * @param name relevant name ot the target element (e.g. of identifier in an expression).
   * @param useQualified if True, use qualified "import modulename" instead of "from modulename import ...".
   */
  public ImportFromExistingAction(@NotNull PsiElement target, @NotNull List<? extends ImportCandidateHolder> sources, @NotNull String name,
                                  boolean useQualified, boolean importLocally) {
    myTarget = target;
    mySources = sources;
    myName = name;
    myUseQualifiedImport = useQualified;
    myImportLocally = importLocally;
  }

  public void onDone(Runnable callback) {
    assert myOnDoneCallback == null;
    myOnDoneCallback = callback;
  }


  /**
   * Alters either target (by qualifying a name) or source (by explicitly importing the name).
   *
   * @return true if action succeeded
   */
  @Override
  public boolean execute() {
    // check if the tree is sane
    PsiDocumentManager.getInstance(myTarget.getProject()).commitAllDocuments();
    PyPsiUtils.assertValid(myTarget);
    if ((myTarget instanceof PyQualifiedExpression) && ((((PyQualifiedExpression)myTarget).isQualified()))) {
      return false; // we cannot be qualified
    }
    for (ImportCandidateHolder item : mySources) {
      PyPsiUtils.assertValid(item.getImportable());
      PyPsiUtils.assertValid(item.getFile());
      final PyImportElement element = item.getImportElement();
      if (element != null) {
        PyPsiUtils.assertValid(element);
      }
    }
    if (mySources.isEmpty()) {
      return false;
    }
    // act
    if (mySources.size() == 1 || ApplicationManager.getApplication().isUnitTestMode()) {
      doWriteAction(mySources.get(0));
    }
    else {
      selectSourceAndDo();
    }
    return true;
  }

  private void selectSourceAndDo() {
    ImportChooser.getInstance()
      .selectImport(mySources, myName, myUseQualifiedImport)
      .onSuccess(candidate -> {
        PsiDocumentManager.getInstance(myTarget.getProject()).commitAllDocuments();
        doWriteAction(candidate);
      });
  }

  private void doIt(final ImportCandidateHolder item) {
    PyImportElement src = item.getImportElement();
    if (src != null) {
      addToExistingImport(src);
    }
    else { // no existing import, add it then use it
      addImportStatement(item);
    }
  }

  private void addImportStatement(ImportCandidateHolder item) {
    final Project project = myTarget.getProject();
    final PyElementGenerator gen = PyElementGenerator.getInstance(project);

    final PsiFileSystemItem filesystemAnchor = ObjectUtils.chooseNotNull(as(item.getImportable(), PsiFileSystemItem.class), item.getFile());
    if (filesystemAnchor == null) {
      return;
    }
    AddImportHelper.ImportPriority priority = AddImportHelper.getImportPriority(myTarget, filesystemAnchor);
    PsiFile file = myTarget.getContainingFile();
    InjectedLanguageManager manager = InjectedLanguageManager.getInstance(project);
    if (manager.isInjectedFragment(file)) {
      file = manager.getTopLevelFile(myTarget);
    }
    // We are trying to import top-level module or package which thus cannot be qualified
    if (PyUtil.isRoot(item.getFile())) {
      if (myImportLocally) {
        AddImportHelper.addLocalImportStatement(myTarget, myName);
      }
      else {
        AddImportHelper.addImportStatement(file, myName, item.getAsName(), priority, myTarget);
      }
    }
    else {
      final QualifiedName path = item.getPath();
      final String qualifiedName = path != null ? path.toString() : "";
      if (myUseQualifiedImport) {
        String nameToImport = qualifiedName;
        if (item.getImportable() instanceof PsiFileSystemItem) {
          nameToImport += "." + myName;
        }
        if (myImportLocally) {
          AddImportHelper.addLocalImportStatement(myTarget, nameToImport);
        }
        else {
          AddImportHelper.addImportStatement(file, nameToImport, item.getAsName(), priority, myTarget);
        }
        myTarget.replace(gen.createExpressionFromText(LanguageLevel.forElement(myTarget), qualifiedName + "." + myName));
      }
      else {
        if (myImportLocally) {
          AddImportHelper.addLocalFromImportStatement(myTarget, qualifiedName, myName);
        }
        else {
          // "Update" scenario takes place inside injected fragments, for normal AST addToExistingImport() will be used instead
          AddImportHelper.addOrUpdateFromImportStatement(file, qualifiedName, myName, item.getAsName(), priority, myTarget);
        }
      }
    }
  }


  private void addToExistingImport(PyImportElement src) {
    final PyElementGenerator gen = PyElementGenerator.getInstance(myTarget.getProject());
    // did user choose 'import' or 'from import'?
    PsiElement parent = src.getParent();
    if (parent instanceof PyFromImportStatement) {
      AddImportHelper.addNameToFromImportStatement((PyFromImportStatement)parent, myName, null);
    }
    else { // just 'import'
      // all we need is to qualify our target
      myTarget.replace(gen.createExpressionFromText(LanguageLevel.forElement(myTarget), src.getVisibleName() + "." + myName));
    }
  }

  private void doWriteAction(final ImportCandidateHolder item) {
    PsiElement src = item.getImportable();
    if (src == null) {
      return;
    }
    WriteCommandAction.writeCommandAction(src.getProject(), myTarget.getContainingFile())
      .withName(PyPsiBundle.message("ACT.CMD.use.import"))
      .run(() -> doIt(item));
    if (myOnDoneCallback != null) {
      myOnDoneCallback.run();
    }
  }

}
