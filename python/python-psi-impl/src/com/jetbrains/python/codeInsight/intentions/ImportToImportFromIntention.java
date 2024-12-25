// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.ImportedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Converts {@code import foo} to {@code from foo import names} or {@code from ... import module} to {@code from ...module import names}.
 * Module names used as qualifiers are removed.
 * <br>
 * <i>NOTE: currently we only check usage of module name in the same file. For re-exported module names this is not sufficient.</i>
 * <br>
 */
public final class ImportToImportFromIntention extends PsiBasedModCommandAction<PyReferenceExpression> {
  private static class IntentionState {
    private final @NotNull PyImportElement myImportElement;
    private final @Nullable QualifiedName myQualifiedName;
    private final @NotNull List<PyReferenceExpression> myReferences = new ArrayList<>();
    private final @NotNull String myNewImportSource;
    // if anything that resolves to our imported module is just an exact reference to that module
    private boolean myHasModuleReference = false;

    IntentionState(@NotNull PyImportElement importElement, @Nullable QualifiedName qualifiedName) {
      int relativeLevel = 0;
      boolean available = false;
      myImportElement = importElement;
      myQualifiedName = qualifiedName;
      final PsiElement parent = myImportElement.getParent();
      if (parent instanceof PyImportStatement) {
        available = true;
      }
      else if (parent instanceof PyFromImportStatement fromImport) {
        relativeLevel = fromImport.getRelativeLevel();
        if (relativeLevel > 0 && fromImport.getImportSource() == null) {
          available = true;
        }
      }
      PyReferenceExpression oldImportSource = myImportElement.getImportReferenceExpression();
      assert oldImportSource != null;
      myNewImportSource = StringUtil.repeat(".", relativeLevel) + PyPsiUtils.toPath(oldImportSource);
      if (available) {
        collectReferencesAndOtherData(oldImportSource); // this will cache data for the invocation
      }
    }

    public boolean isAvailable() {
      return !myReferences.isEmpty();
    }

    private void collectReferencesAndOtherData(@NotNull PyReferenceExpression oldImportSource) {
      String qualifierName = getQualifierName(myImportElement);
      PsiElement referee = oldImportSource.getReference().resolve();
      myHasModuleReference = false;
      if (referee != null && qualifierName != null) {
        PsiTreeUtil.processElements(oldImportSource.getContainingFile(), new PsiElementProcessor<>() {
          @Override
          public boolean execute(@NotNull PsiElement element) {
            if (element instanceof PyReferenceExpression ref && PsiTreeUtil.getParentOfType(element, PyImportElement.class) == null) {
              if (qualifierName.equals(PyPsiUtils.toPath(ref))) {  // filter out other names that might resolve to our target
                if (ref.getParent() instanceof PyReferenceExpression) { // really qualified by us, not just referencing?
                  final PsiElement resolved = ref.getReference().resolve();
                  if (resolved == referee) myReferences.add(ref);
                }
                else {
                  myHasModuleReference = true;
                }
              }
            }
            return true;
          }
        });
      }
    }

    public boolean allReferencesHaveSameName() {
      return myReferences.size() == getSameNameReferences().size();
    }

    private @NotNull Collection<PyReferenceExpression> getSameNameReferences() {
      if (myQualifiedName == null) return myReferences;
      return ContainerUtil.filter(myReferences,
                                  ref -> ref.getParent() instanceof PyReferenceExpression parentRef &&
                                         myQualifiedName.equals(parentRef.asQualifiedName()));
    }

    public void invoke(boolean unqualifyAll) {
      final Project project = myImportElement.getProject();

      final PyElementGenerator generator = PyElementGenerator.getInstance(project);
      final LanguageLevel languageLevel = LanguageLevel.forElement(myImportElement);

      // usages of imported name are qualifiers; what they refer to?
      try {
        // remember names and make them drop qualifiers
        final Set<String> usedNames = new HashSet<>();
        Collection<PyReferenceExpression> referencesToUpdate = unqualifyAll ? myReferences : getSameNameReferences();
        for (PyReferenceExpression ref : referencesToUpdate) {
          final PsiElement parentElt = ref.getParent();
          assert parentElt instanceof PyReferenceExpression: parentElt.getClass();
          final String nameUsed = Objects.requireNonNull(((PyReferenceExpression)parentElt).getReferencedName());
          usedNames.add(nameUsed);
          final PyElement newReference = generator.createExpressionFromText(languageLevel, nameUsed);
          parentElt.replace(newReference);
        }

        // create a separate import stmt for the module
        final PsiElement importer = myImportElement.getParent();
        final PyImportStatementBase importStatement;
        final PyImportElement[] importElements;
        if (importer instanceof PyImportStatement qualifiedImportStatement) {
          importStatement = qualifiedImportStatement;
          importElements = importStatement.getImportElements();
        }
        else if (importer instanceof PyFromImportStatement fromImportStatement) {
          importStatement = fromImportStatement;
          importElements = importStatement.getImportElements();
        }
        else {
          throw new IncorrectOperationException("Not an import at all");
        }
        final PyFromImportStatement newImportStatement =
          generator.createFromImportStatement(languageLevel, myNewImportSource, StringUtil.join(usedNames, ", "), null);
        final PsiElement parent = importStatement.getParent();
        boolean canRemoveImport = !myHasModuleReference && referencesToUpdate.size() == myReferences.size();
        if (importElements.length == 1) {
          if (!canRemoveImport) {
            parent.addAfter(newImportStatement, importStatement); // add 'import from': we need the module imported as is
          }
          else { // replace entire existing import
            importStatement.replace(newImportStatement);
          }
        }
        else {
          if (canRemoveImport) {
            // cut the module out of import, add a from-import.
            myImportElement.delete();
          }
          parent.addAfter(newImportStatement, importStatement);
        }
      }
      catch (IncorrectOperationException ignored) {
        PythonUiService.getInstance().showBalloonWarning(project, PyPsiBundle.message("QFIX.action.failed"));
      }
    }


    public @NotNull @IntentionName String getText() {
      if (myQualifiedName == null) {
        return PyPsiBundle.message("INTN.convert.to.from.import", myNewImportSource, "...");
      }
      else {
        return PyPsiBundle.message("INTN.remove.qualifier", getQualifierName(myImportElement));
      }
    }

    private static @Nullable String getQualifierName(@NotNull PyImportElement importElement) {
      String asName = importElement.getAsName();
      if (asName != null) {
        return asName;
      }
      QualifiedName importedQName = importElement.getImportedQName();
      if (importedQName != null) {
        return importedQName.toString();
      }
      return null;
    }
  }

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("INTN.NAME.convert.import.unqualify");
  }

  private static @Nullable IntentionState createState(@NotNull PyReferenceExpression refExprUnderCaret) {
    final PyImportElement importElement = PsiTreeUtil.getParentOfType(refExprUnderCaret, PyImportElement.class);
    if (importElement != null) {
      return new IntentionState(importElement, null);
    }
    PyReferenceExpression reference =
      Objects.requireNonNullElse(PsiTreeUtil.getTopmostParentOfType(refExprUnderCaret, PyReferenceExpression.class), refExprUnderCaret);
    while (reference.getQualifier() instanceof PyReferenceExpression refQualifier) {
      for (ResolveResult rr : refQualifier.getReference().multiResolve(false)) {
        if (rr.isValidResult() &&
            rr instanceof ImportedResolveResult irr &&
            irr.getDefiner() instanceof PyImportElement importDefiner &&
            importDefiner.getContainingFile() == refExprUnderCaret.getContainingFile()) {
          return new IntentionState(importDefiner, reference.asQualifiedName());
        }
      }
      reference = refQualifier;
    }
    return null;
  }

  private final @NotNull ThreeState myUnqualifyAll;

  @SuppressWarnings("unused")
  public ImportToImportFromIntention() {
    this(ThreeState.UNSURE);
  }

  private ImportToImportFromIntention(@NotNull ThreeState unqualifyAll) {
    super(PyReferenceExpression.class);
    myUnqualifyAll = unqualifyAll;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PyReferenceExpression element) {
    if (!(context.file() instanceof PyFile)) {
      return null;
    }

    final IntentionState state = createState(element);
    if (state != null && state.isAvailable()) {
      return Presentation.of(
        switch (myUnqualifyAll) {
          case YES -> PyPsiBundle.message("INTN.remove.qualifier.from.all.usages");
          case NO -> PyPsiBundle.message("INTN.remove.qualifier.from.this.name");
          case UNSURE -> state.getText();
        }
      );
    }
    return null;
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PyReferenceExpression element) {
    return switch (myUnqualifyAll) {
      case YES -> invoke(element, true);
      case NO -> invoke(element, false);
      case UNSURE -> {
        IntentionState state = createAndCheckState(element);
        if (state.allReferencesHaveSameName()) {
          yield invoke(element, true);
        }
        else {
          yield new ModChooseAction(PyPsiBundle.message("INTN.multiple.usages.of.import.found"),
                                    List.of(new ImportToImportFromIntention(ThreeState.YES),
                                            new ImportToImportFromIntention(ThreeState.NO)));
        }
      }
    };
  }

  private static @NotNull ModCommand invoke(@NotNull PyReferenceExpression refExpr, boolean unqualifyAll) {
    return ModCommand.psiUpdate(refExpr, refExprCopy -> {
      createAndCheckState(refExprCopy).invoke(unqualifyAll);
    });
  }

  private static @NotNull IntentionState createAndCheckState(@NotNull PyReferenceExpression refExpr) {
    IntentionState state = createState(refExpr);
    if (state == null || !state.isAvailable()) {
      throw new IllegalStateException();
    }
    return state;
  }
}
