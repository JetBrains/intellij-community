// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThreeState;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.PyUtil.sure;

/**
 * Converts {@code import foo} to {@code from foo import names} or {@code from ... import module} to {@code from ...module import names}.
 * Module names used as qualifiers are removed.
 * <br>
 * <i>NOTE: currently we only check usage of module name in the same file. For re-exported module names this is not sufficient.</i>
 * <br>
 */
public final class ImportToImportFromIntention extends PsiBasedModCommandAction<PsiElement> {
  private static class IntentionState {
    private String myModuleName = null;
    private final @NotNull PyImportElement myImportElement;
    private final @Nullable QualifiedName myQualifiedName;
    private final @NotNull List<PyReferenceExpression> myReferences = new ArrayList<>();
    private boolean myHasModuleReference = false;
      // is anything that resolves to our imported module is just an exact reference to that module
    private int myRelativeLevel; // true if "from ... import"

    IntentionState(@NotNull PsiFile file, @NotNull PyImportElement importElement, @Nullable QualifiedName qualifiedName) {
      boolean available = false;
      myImportElement = importElement;
      myQualifiedName = qualifiedName;
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
      if (available) {
        collectReferencesAndOtherData(file); // this will cache data for the invocation
      }
    }

    public boolean isAvailable() {
      return !myReferences.isEmpty();
    }

    private void collectReferencesAndOtherData(PsiFile file) {
      // usages of imported name are qualifiers; what they refer to?
      final PyReferenceExpression importReference = myImportElement.getImportReferenceExpression();
      if (importReference != null) {
        myModuleName = PyPsiUtils.toPath(importReference);
        String qualifierName = getQualifierName(myImportElement);
        PsiElement referee = importReference.getReference().resolve();
        myHasModuleReference = false;
        if (referee != null && qualifierName != null) {
          PsiTreeUtil.processElements(file, new PsiElementProcessor<>() {
            @Override
            public boolean execute(@NotNull PsiElement element) {
              if (element instanceof PyReferenceExpression ref && PsiTreeUtil.getParentOfType(element, PyImportElement.class) == null) {
                if (qualifierName.equals(PyPsiUtils.toPath(ref))) {  // filter out other names that might resolve to our target
                  final PsiElement parentElt = ref.getParent();
                  if (parentElt instanceof PyReferenceExpression) { // really qualified by us, not just referencing?
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
    }

    public boolean allReferencesHaveSameName() {
      return myReferences.size() == getSameNameReferences().size();
    }

    @NotNull
    private Collection<PyReferenceExpression> getSameNameReferences() {
      if (myQualifiedName == null) return myReferences;
      return myReferences.stream()
        .filter(
          ref -> ref.getParent() instanceof PyReferenceExpression parentRef && Objects.equals(myQualifiedName, parentRef.asQualifiedName())
        )
        .toList();
    }

    public void invoke(boolean unqualifyAll) {
      sure(myImportElement.getImportReferenceExpression());
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
        boolean canRemoveImport = !myHasModuleReference && referencesToUpdate.size() == myReferences.size();
        if (importElements.length == 1) {
          if (!canRemoveImport) {
            parent.addAfter(newImportStatement, importStatement); // add 'import from': we need the module imported as is
          }
          else { // replace entire existing import
            sure(parent.getNode()).replaceChild(sure(importStatement.getNode()), sure(newImportStatement.getNode()));
            // import_statement.replace(from_import_stmt);
          }
        }
        else {
          if (canRemoveImport) {
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
      if (myQualifiedName == null) {
        String moduleName = Optional.ofNullable(myModuleName).orElse("?");
        return PyPsiBundle.message("INTN.convert.to.from.import", getDots() + moduleName, "...");
      }
      else {
        return PyPsiBundle.message("INTN.remove.qualifier", getQualifierName(myImportElement));
      }
    }

    @NotNull
    private String getDots() {
      String dots = "";
      for (int i = 0; i < myRelativeLevel; i += 1) {
        dots += "."; // this generally runs 1-2 times, so it's cheaper than allocating a StringBuilder
      }
      return dots;
    }

    private static String getQualifierName(PyImportElement importElement) {
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
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.NAME.convert.import.unqualify");
  }

  @Nullable
  private static IntentionState createState(@NotNull PsiFile file, int offset) {
    final PsiElement elementAtCaret = file.findElementAt(offset);
    final PyImportElement importElement = PsiTreeUtil.getParentOfType(elementAtCaret, PyImportElement.class);
    PyPsiUtils.assertValid(importElement);
    if (importElement != null) {
      return new IntentionState(file, importElement, null);
    }
    PyReferenceExpression ref = PsiTreeUtil.getParentOfType(elementAtCaret, PyReferenceExpression.class);
    PyPsiUtils.assertValid(ref);
    if (ref != null) {
      while (ref.getParent() instanceof PyReferenceExpression parentRef) {
        ref = parentRef;
      }
      while (ref.getQualifier() instanceof PyReferenceExpression refQualifier) {
        ResolveResult[] resolved = refQualifier.getReference().multiResolve(false);
        for (ResolveResult rr : resolved) {
          if (rr.isValidResult()) {
            if (rr.getElement() instanceof PyImportElement && rr.getElement().getContainingFile() == file) {
              return new IntentionState(file, (PyImportElement)rr.getElement(), ref.asQualifiedName());
            }
          }
        }
        ref = refQualifier;
      }
    }
    return null;
  }

  private final @NotNull ThreeState myUnqualifyAll;

  @SuppressWarnings("unused")
  public ImportToImportFromIntention() {
    this(ThreeState.UNSURE);
  }

  private ImportToImportFromIntention(@NotNull ThreeState unqualifyAll) {
    super(PsiElement.class);
    myUnqualifyAll = unqualifyAll;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    if (!(context.file() instanceof PyFile)) {
      return null;
    }

    final IntentionState state = createState(context.file(), context.offset());
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
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiElement element) {
    return switch (myUnqualifyAll) {
      case YES -> invoke(context, true);
      case NO -> invoke(context, false);
      case UNSURE -> {
        IntentionState state = createAndCheckState(context.file(), context.offset());
        if (state.allReferencesHaveSameName()) {
          yield invoke(context, true);
        }
        else {
          yield new ModChooseAction(PyPsiBundle.message("INTN.multiple.usages.of.import.found"),
                                    List.of(new ImportToImportFromIntention(ThreeState.YES),
                                            new ImportToImportFromIntention(ThreeState.NO)));
        }
      }
    };
  }

  @NotNull
  private static ModCommand invoke(@NotNull ActionContext context, boolean unqualifyAll) {
    return ModCommand.psiUpdate(context.file(), fileCopy -> {
      createAndCheckState(fileCopy, context.offset()).invoke(unqualifyAll);
    });
  }

  @NotNull
  private static IntentionState createAndCheckState(@NotNull PsiFile file, int offset) {
    IntentionState state = createState(file, offset);
    if (state == null || !state.isAvailable()) {
      throw new IllegalStateException();
    }
    return state;
  }
}
