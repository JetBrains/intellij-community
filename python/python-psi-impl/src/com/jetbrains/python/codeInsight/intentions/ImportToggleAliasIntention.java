// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.lang.ASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.codeInsight.intentions.DeclarationConflictChecker.findDefinitions;
import static com.jetbrains.python.psi.PyUtil.sure;

/**
 * Adds an alias to "import foo" or "from foo import bar" import elements, or removes it if it's already present.
 */
public final class ImportToggleAliasIntention extends PyBaseIntentionAction {
  private static class IntentionState {
    private PyImportElement myImportElement;
    private PyFromImportStatement myFromImportStatement;
    private PyImportStatement myImportStatement;
    private String myAlias;

    private static IntentionState fromContext(Editor editor, PsiFile file) {
      IntentionState state = new IntentionState();
      state.myImportElement = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyImportElement.class);
      PyPsiUtils.assertValid(state.myImportElement);
      if (state.myImportElement != null) {
        PyTargetExpression target = state.myImportElement.getAsNameElement();
        PyPsiUtils.assertValid(target);
        if (target != null) state.myAlias = target.getName();
        else state.myAlias = null;
        state.myFromImportStatement = PsiTreeUtil.getParentOfType(state.myImportElement, PyFromImportStatement.class);
        state.myImportStatement = PsiTreeUtil.getParentOfType(state.myImportElement, PyImportStatement.class);
      }
      return state;
    }

    public boolean isAvailable() {
      if (myFromImportStatement != null) {
        PyPsiUtils.assertValid(myFromImportStatement);
        if (!myFromImportStatement.isValid() || myFromImportStatement.isFromFuture()) {
          return false;
        }
      }
      else {
        PyPsiUtils.assertValid(myImportStatement);
        if (myImportStatement == null || !myImportStatement.isValid()) {
          return false;
        }
      }
      final PyReferenceExpression referenceExpression = myImportElement.getImportReferenceExpression();
      if (referenceExpression == null || referenceExpression.getReference().resolve() == null) {
        return false;
      }
      return true;
    }

    public @NotNull @IntentionName String getText() {
      String add_name = PyPsiBundle.message("INTN.add.import.alias");
      if (myImportElement != null) {
        PyReferenceExpression refex = myImportElement.getImportReferenceExpression();
        if (refex != null) {
          add_name = PyPsiBundle.message("INTN.add.import.alias.to.name", refex.getText());
        }
      }
      return myAlias == null? add_name : PyPsiBundle.message("INTN.remove.import.alias", myAlias);
    }
  }

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("INTN.NAME.toggle.import.alias");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (!(psiFile instanceof PyFile)) {
      return false;
    }

    IntentionState state = IntentionState.fromContext(editor, psiFile);
    setText(state.getText());
    return state.isAvailable();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return currentFile;
  }

  @Override
  public void doInvoke(final @NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    // sanity check: isAvailable must have set it.
    final IntentionState state = IntentionState.fromContext(editor, file);
    //
    final String target_name; // we set in in the source
    final String remove_name; // we replace it in the source
    PyReferenceExpression reference = sure(state.myImportElement.getImportReferenceExpression());
    // search for references to us with the right name
    try {
      String imported_name = PyPsiUtils.toPath(reference);
      if (state.myAlias != null) {
        // have to remove alias, rename everything to original
        target_name = imported_name;
        remove_name = state.myAlias;
      }
      else {
        // ask for and add alias
        String alias = PythonUiService.getInstance().showInputDialog(project,
                                                                     PyPsiBundle.message("INTN.add.import.alias.dialog.message", imported_name),
                                                                     PyPsiBundle.message("INTN.add.import.alias.title"), "", new InputValidator() {
            @Override
            public boolean checkInput(String inputString) {
              return PyNames.isIdentifier(inputString);
            }

            @Override
            public boolean canClose(String inputString) {
              return PyNames.isIdentifier(inputString);
            }
        });
        if (alias == null) {
          return;
        }
        target_name = alias;
        remove_name = imported_name;
      }
      final PsiElement referee = reference.getReference().resolve();
      if (referee != null) {
        final Collection<PsiReference> references = new ArrayList<>();
        final ScopeOwner scope = PsiTreeUtil.getParentOfType(state.myImportElement, ScopeOwner.class);
        PsiTreeUtil.processElements(scope, new PsiElementProcessor<>() {
          @Override
          public boolean execute(@NotNull PsiElement element) {
            getReferences(element);
            if (element instanceof PyStringLiteralExpression host) {
              final List<Pair<PsiElement, TextRange>> files =
                InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(host);
              if (files != null) {
                for (Pair<PsiElement, TextRange> pair : files) {
                  final PsiElement first = pair.getFirst();
                  if (first instanceof ScopeOwner scopeOwner) {
                    PsiTreeUtil.processElements(scopeOwner, new PsiElementProcessor<>() {
                      @Override
                      public boolean execute(@NotNull PsiElement element) {
                        getReferences(element);
                        return true;
                      }
                    });
                  }
                }
              }
            }
            return true;
          }

          private void getReferences(PsiElement element) {
            if (element instanceof PyReferenceExpression ref && PsiTreeUtil.getParentOfType(element,
                                                                                            PyImportElement.class) == null) {
              if (remove_name.equals(PyPsiUtils.toPath(ref))) {  // filter out other names that might resolve to our target
                PsiElement resolved = ref.getReference().resolve();
                if (resolved == referee ||
                    resolved instanceof PyFunction && PyUtil.turnConstructorIntoClass((PyFunction)resolved) == referee) {
                  references.add(ref.getReference());
                }
              }
            }
          }
        });
        // no references here is OK by us.
        if (PythonUiService.getInstance().showConflicts(project, findDefinitions(target_name, references, Collections.emptySet()), target_name, null)) {
          return; // got conflicts
        }

        // alter the import element
        PyElementGenerator generator = PyElementGenerator.getInstance(project);
        final LanguageLevel languageLevel = LanguageLevel.forElement(state.myImportElement);
        if (state.myAlias != null) {
          // remove alias
          WriteAction.run(() -> {
            ASTNode node = sure(state.myImportElement.getNode());
            ASTNode parent = sure(node.getTreeParent());
            node = sure(node.getFirstChildNode()); // this is the reference
            node = sure(node.getTreeNext()); // things past the reference: space, 'as', and alias
            parent.removeRange(node, null);
          });
        }
        else {
          // add alias
          WriteAction.run(() -> {
            ASTNode my_ielt_node = sure(state.myImportElement.getNode());
            PyImportElement fountain = generator.createFromText(languageLevel, PyImportElement.class, "import foo as "+target_name, new int[]{0,2});
            ASTNode graft_node = sure(fountain.getNode()); // at import elt
            graft_node = sure(graft_node.getFirstChildNode()); // at ref
            graft_node = sure(graft_node.getTreeNext()); // space
            my_ielt_node.addChild((ASTNode)graft_node.clone());
            graft_node = sure(graft_node.getTreeNext()); // 'as'
            my_ielt_node.addChild((ASTNode)graft_node.clone());
            graft_node = sure(graft_node.getTreeNext()); // space
            my_ielt_node.addChild((ASTNode)graft_node.clone());
            graft_node = sure(graft_node.getTreeNext()); // alias
            my_ielt_node.addChild((ASTNode)graft_node.clone());
          });
        }
        // alter references
        for (PsiReference ref : references) {
          ASTNode ref_name_node = sure(sure(ref.getElement()).getNode());
          ASTNode parent = sure(ref_name_node.getTreeParent());
          ASTNode new_name_node = generator.createExpressionFromText(languageLevel, target_name).getNode();
          assert new_name_node != null;
          WriteAction.run(() -> parent.replaceChild(ref_name_node, new_name_node));
        }
      }
    }
    catch (IncorrectOperationException ignored) {
      PythonUiService.getInstance().showBalloonWarning(project, PyPsiBundle.message("QFIX.action.failed"));
    }
  }
}
