// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.jetbrains.python.psi.PyUtil.sure;

/**
 * Converts {@code import foo} to {@code from foo import names} or {@code from ... import module} to {@code from ...module import names}.
 * Module names used as qualifiers are removed.
 * <br>
 * <i>NOTE: currently we only check usage of module name in the same file. For re-exported module names this is not sufficient.</i>
 * <br>
 */
public class ImportToImportFromIntention extends PyBaseIntentionAction {

  private static class IntentionState {
    private String myModuleName = null;
    private String myQualifierName = null;
    private PsiElement myReferee = null;
    private PyImportElement myImportElement = null;
    private Collection<PsiReference> myReferences = null;
    private boolean myHasModuleReference = false;
      // is anything that resolves to our imported module is just an exact reference to that module
    private int myRelativeLevel; // true if "from ... import"

    IntentionState(@NotNull Editor editor, @NotNull PsiFile file) {
      boolean available = false;
      myImportElement = findImportElement(editor, file);
      if (myImportElement != null) {
        final PsiElement parent = myImportElement.getParent();
        if (parent instanceof PyImportStatement) {
          myRelativeLevel = 0;
          available = true;
        }
        else if (parent instanceof PyFromImportStatement fromImport) {
          final int relativeLevel = fromImport.getRelativeLevel();
          PyPsiUtils.assertValid(fromImport);
          if (fromImport.isValid() && relativeLevel > 0 && fromImport.getImportSource() == null) {
            myRelativeLevel = relativeLevel;
            available = true;
          }
        }
      }
      if (available) {
        collectReferencesAndOtherData(file); // this will cache data for the invocation
      }
    }

    public boolean isAvailable() {
      return myReferences != null && myReferences.size() > 0;
    }

    private void collectReferencesAndOtherData(PsiFile file) {
      //PyImportElement myImportElement = findImportElement(editor, file);
      assert myImportElement != null : "isAvailable() must have returned true, but myImportElement is null";

      // usages of imported name are qualifiers; what they refer to?
      final PyReferenceExpression importReference = myImportElement.getImportReferenceExpression();
      if (importReference != null) {
        myModuleName = PyPsiUtils.toPath(importReference);
        myQualifierName = myImportElement.getVisibleName();
        myReferee = importReference.getReference().resolve();
        myHasModuleReference = false;
        if (myReferee != null && myModuleName != null && myQualifierName != null) {
          final Collection<PsiReference> references = new ArrayList<>();
          PsiTreeUtil.processElements(file, new PsiElementProcessor() {
            @Override
            public boolean execute(@NotNull PsiElement element) {
              if (element instanceof PyReferenceExpression ref && PsiTreeUtil.getParentOfType(element, PyImportElement.class) == null) {
                if (myQualifierName.equals(PyPsiUtils.toPath(ref))) {  // filter out other names that might resolve to our target
                  final PsiElement parentElt = ref.getParent();
                  if (parentElt instanceof PyQualifiedExpression) { // really qualified by us, not just referencing?
                    final PsiElement resolved = ref.getReference().resolve();
                    if (resolved == myReferee) references.add(ref.getReference());
                  }
                  else {
                    myHasModuleReference = true;
                  }
                }
              }
              return true;
            }
          });
          myReferences = references;
        }
      }
    }

    public void invoke() {
      assert myImportElement != null : "isAvailable() must have returned true, but myImportElement is null";
      sure(myImportElement.getImportReferenceExpression());
      final Project project = myImportElement.getProject();

      final PyElementGenerator generator = PyElementGenerator.getInstance(project);
      final LanguageLevel languageLevel = LanguageLevel.forElement(myImportElement);

      // usages of imported name are qualifiers; what they refer to?
      try {
        // remember names and make them drop qualifiers
        final Set<String> usedNames = new HashSet<>();
        for (PsiReference ref : myReferences) {
          final PsiElement elt = ref.getElement();
          final PsiElement parentElt = elt.getParent();
          // TODO: find ident node more properly
          final String nameUsed = sure(sure(parentElt).getLastChild()).getText();
          usedNames.add(nameUsed);
          if (!FileModificationService.getInstance().preparePsiElementForWrite(elt)) {
            return;
          }
          assert parentElt instanceof PyReferenceExpression: parentElt.getClass();
          final PyElement newReference = generator.createExpressionFromText(languageLevel, nameUsed);
          parentElt.replace(newReference);
        }

        // create a separate import stmt for the module
        final PsiElement importer = myImportElement.getParent();
        final PyStatement importStatement;
        final PyImportElement[] importElements;
        if (importer instanceof PyImportStatement) {
          importStatement = (PyImportStatement)importer;
          importElements = ((PyImportStatement)importStatement).getImportElements();
        }
        else if (importer instanceof PyFromImportStatement) {
          importStatement = (PyFromImportStatement)importer;
          importElements = ((PyFromImportStatement)importStatement).getImportElements();
        }
        else {
          throw new IncorrectOperationException("Not an import at all");
        }
        final PyFromImportStatement newImportStatement =
          generator.createFromImportStatement(languageLevel, getDots() + myModuleName, StringUtil.join(usedNames, ", "), null);
        final PsiElement parent = importStatement.getParent();
        sure(parent);
        sure(parent.isValid());
        if (importElements.length == 1) {
          if (myHasModuleReference) {
            parent.addAfter(newImportStatement, importStatement); // add 'import from': we need the module imported as is
          }
          else { // replace entire existing import
            sure(parent.getNode()).replaceChild(sure(importStatement.getNode()), sure(newImportStatement.getNode()));
            // import_statement.replace(from_import_stmt);
          }
        }
        else {
          if (!myHasModuleReference) {
            // cut the module out of import, add a from-import.
            for (PyImportElement pie : importElements) {
              if (pie == myImportElement) {
                pie.delete();
                break;
              }
            }
          }
          parent.addAfter(newImportStatement, importStatement);
        }
      }
      catch (IncorrectOperationException ignored) {
        PythonUiService.getInstance().showBalloonWarning(project, PyPsiBundle.message("QFIX.action.failed"));
      }
    }


    @NotNull
    public @IntentionName String getText() {
      String moduleName = "?";
      if (myImportElement != null) {
        final PyReferenceExpression reference = myImportElement.getImportReferenceExpression();
        if (reference != null) {
          moduleName = PyPsiUtils.toPath(reference);
        }
      }
      return PyPsiBundle.message("INTN.convert.to.from.import", getDots() + moduleName, "...");
    }

    @NotNull
    private String getDots() {
      String dots = "";
      for (int i = 0; i < myRelativeLevel; i += 1) {
        dots += "."; // this generally runs 1-2 times, so it's cheaper than allocating a StringBuilder
      }
      return dots;
    }
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.NAME.convert.import.unqualify");
  }

  @Nullable
  private static PyImportElement findImportElement(@NotNull Editor editor, @NotNull PsiFile file) {
    final PsiElement elementAtCaret = file.findElementAt(editor.getCaretModel().getOffset());
    final PyImportElement importElement = PsiTreeUtil.getParentOfType(elementAtCaret, PyImportElement.class);
    PyPsiUtils.assertValid(importElement);
    if (importElement != null && importElement.isValid()) {
      return importElement;
    }
    else {
      return null;
    }
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }

    final IntentionState state = new IntentionState(editor, file);
    if (state.isAvailable()) {
      setText(state.getText());
      return true;
    }
    return false;
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final IntentionState state = new IntentionState(editor, file);
    state.invoke();
  }
}
