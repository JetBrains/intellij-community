// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.lang.ASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SlowOperations;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import org.jetbrains.annotations.ApiStatus;
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
@ApiStatus.Internal
public final class ImportToggleAliasIntention extends PyBaseIntentionAction {
  private static class IntentionState {
    private PyImportElement myImportElement;
    private PyFromImportStatement myFromImportStatement;
    private PyImportStatement myImportStatement;
    private String myAlias;

    private static IntentionState fromContext(Editor editor, PsiFile file) {
      PyImportElement importElement =
        PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyImportElement.class);
      PyPsiUtils.assertValid(importElement);
      return forImportElement(importElement);
    }

    private static IntentionState forImportElement(@Nullable PyImportElement importElement) {
      IntentionState state = new IntentionState();
      state.myImportElement = importElement;
      if (importElement != null) {
        PyTargetExpression target = importElement.getAsNameElement();
        PyPsiUtils.assertValid(target);
        if (target != null) {
          state.myAlias = target.getName();
        }
        else {
          state.myAlias = null;
        }
        state.myFromImportStatement = PsiTreeUtil.getParentOfType(importElement, PyFromImportStatement.class);
        state.myImportStatement = PsiTreeUtil.getParentOfType(importElement, PyImportStatement.class);
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
      if (myImportElement.getImportReferenceExpression() == null) {
        return false;
      }
      // The imported module or name is not resolved here. An alias renames the name that the import binds,
      // and this works the same way when the import target is unknown.
      // `import foo as ` already carries an `as` without a name. A second one would give `import foo as  as foo`.
      return myAlias != null || myImportElement.getNode().findChildByType(PyTokenTypes.AS_KEYWORD) == null;
    }

    public @NotNull @IntentionName String getText() {
      String add_name = PyPsiBundle.message("INTN.add.import.alias");
      if (myImportElement != null) {
        PyReferenceExpression refex = myImportElement.getImportReferenceExpression();
        if (refex != null) {
          add_name = PyPsiBundle.message("INTN.add.import.alias.to.name", refex.getText());
        }
      }
      return myAlias == null ? add_name : PyPsiBundle.message("INTN.remove.import.alias", myAlias);
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
    final String alias = state.myAlias;
    try {
      final String importedName = PyPsiUtils.toPath(sure(state.myImportElement.getImportReferenceExpression()));
      final String targetName; // we set it in the source
      final String removeName; // we replace it in the source
      if (alias == null) {
        // Add the alias under a default name. The in-place template started below lets the user replace it.
        targetName = getLastComponent(importedName);
        removeName = importedName;
      }
      else {
        // have to remove alias, rename everything to original
        targetName = importedName;
        removeName = alias;
      }
      if (!toggleAlias(project, state, targetName, removeName, alias != null)) {
        return; // got conflicts
      }
      // The platform renders no preview for this intention, because it does not start in a write action.
      // Preview must still not reach the template or the dialog, because its editor is an imaginary one.
      if (alias == null && !IntentionPreviewUtils.isPreviewElement(file)) {
        startInplaceAliasRename(project, editor, file, state.myImportElement, importedName);
      }
    }
    catch (IncorrectOperationException ignored) {
      PythonUiService.getInstance().showBalloonWarning(project, PyPsiBundle.message("QFIX.action.failed"));
    }
  }

  /**
   * Adds the alias {@code targetName} to the import element, or removes the alias it already has, and renames
   * every usage of {@code removeName} in the enclosing scope to {@code targetName}.
   * A new alias that changes the visible name gets a number suffix when another declaration already binds {@code targetName}.
   *
   * @param checkConflicts ask the user about a name that {@code targetName} would clash with
   * @return {@code false} if the user cancelled on such a conflict, {@code true} if the import element was changed
   */
  private static boolean toggleAlias(@NotNull Project project, @NotNull IntentionState state, @NotNull String targetName,
                                     @NotNull String removeName, boolean checkConflicts) {
    final PyReferenceExpression reference = sure(state.myImportElement.getImportReferenceExpression());
    final PsiElement referee = reference.getReference().resolve();
    final Collection<PsiReference> references = collectReferences(project, state, removeName, referee);
    // no references here is OK by us.
    final String newName = state.myAlias == null && !targetName.equals(removeName)
                           ? findFreeName(targetName, references, state.myImportElement)
                           : targetName;
    if (checkConflicts && PythonUiService.getInstance()
      .showConflicts(project, findDefinitions(targetName, references, Collections.emptySet()), targetName, null)) {
      return false;
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
        PyImportElement fountain =
          generator.createFromText(languageLevel, PyImportElement.class, "import foo as " + newName, new int[]{0, 2});
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
      ASTNode new_name_node = generator.createExpressionFromText(languageLevel, newName).getNode();
      assert new_name_node != null;
      WriteAction.run(() -> parent.replaceChild(ref_name_node, new_name_node));
    }
    return true;
  }

  /**
   * Returns {@code name}, or {@code name} with the smallest number suffix, that no declaration binds at the import or at a usage.
   * A placeholder alias that a declaration hides at a usage changes what the usage means, and the rename cannot reach the usage.
   */
  private static @NotNull String findFreeName(@NotNull String name, @NotNull Collection<PsiReference> references,
                                              @NotNull PyImportElement importElement) {
    final ScopeOwner importScope = ScopeUtil.getScopeOwner(importElement);
    String candidate = name;
    for (int i = 1; isBound(candidate, references, importScope); i++) {
      candidate = name + i;
    }
    return candidate;
  }

  private static boolean isBound(@NotNull String name, @NotNull Collection<PsiReference> references, @Nullable ScopeOwner importScope) {
    return !findDefinitions(name, references, Collections.emptySet()).isEmpty() ||
           importScope != null && !PyResolveUtil.resolveLocally(importScope, name).isEmpty();
  }

  /** Finds the usages of {@code removeName} that the import element under change binds. */
  private static @NotNull Collection<PsiReference> collectReferences(@NotNull Project project, @NotNull IntentionState state,
                                                                     @NotNull String removeName, @Nullable PsiElement referee) {
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
        // The module reference of a `from` import is a child of the statement and not of an import element,
        // so the whole statement is skipped here and not only the import elements in it.
        if (element instanceof PyReferenceExpression ref && PsiTreeUtil.getParentOfType(element,
                                                                                        PyImportStatementBase.class) == null) {
          if (removeName.equals(PyPsiUtils.toPath(ref))) {  // filter out other names that might resolve to our target
            if (isOurReference(ref, ref.getReference().resolve(), referee, state)) {
              references.add(ref.getReference());
            }
          }
        }
      }
    });
    return references;
  }

  /** Tells whether a usage of the import's visible name belongs to that import and not to another declaration. */
  private static boolean isOurReference(@NotNull PyReferenceExpression ref, @Nullable PsiElement resolved,
                                        @Nullable PsiElement referee, @NotNull IntentionState state) {
    if (referee != null) {
      return resolved == referee ||
             resolved instanceof PyFunction function && PyUtil.turnConstructorIntoClass(function) == referee;
    }
    // The imported module or name is unknown, so resolution cannot tell the usages apart. Take a plain
    // dotted name only. `f().json` reports the path "json" as well, and a rewrite would drop the call
    // in front of the name.
    if (ref.asQualifiedName() == null) {
      return false;
    }
    // Such a name is ours when it and its first name resolve into the import statement or resolve to nothing.
    // Another declaration that hides the import resolves to that declaration instead. For `x.y` under a
    // parameter `x`, only the first name resolves to the parameter.
    final PsiElement statement = state.myFromImportStatement != null ? state.myFromImportStatement : state.myImportStatement;
    final PyExpression firstName = PyPsiUtils.getFirstQualifier(ref);
    final PsiElement firstNameTarget = firstName instanceof PyReferenceExpression firstRef ? firstRef.getReference().resolve() : null;
    return isNothingOrInside(resolved, statement) && isNothingOrInside(firstNameTarget, statement);
  }

  /** The first name {@code x} of an unresolved {@code import x.y} resolves to a {@link PyImportedModule} outside the PSI tree. */
  private static boolean isNothingOrInside(@Nullable PsiElement resolved, @Nullable PsiElement statement) {
    if (resolved == null) {
      return true;
    }
    final PsiElement element = resolved instanceof PyImportedModule module ? module.getImportElement() : resolved;
    return element != null && statement != null && PsiTreeUtil.isAncestor(statement, element, false);
  }

  private static @NotNull String getLastComponent(@NotNull String dottedName) {
    int dot = dottedName.lastIndexOf('.');
    return dot >= 0 ? dottedName.substring(dot + 1) : dottedName;
  }

  private static @NotNull InputValidator aliasInputValidator() {
    return new InputValidator() {
      @Override
      public boolean checkInput(String inputString) {
        return PyNames.isIdentifier(inputString);
      }

      @Override
      public boolean canClose(String inputString) {
        return PyNames.isIdentifier(inputString);
      }
    };
  }

  private static void startInplaceAliasRename(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file,
                                              @NotNull PyImportElement importElement, @NotNull String importedName) {
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    PyTargetExpression alias = importElement.getAsNameElement();
    if (alias == null) {
      return;
    }
    editor.getCaretModel().moveToOffset(alias.getTextOffset());
    // The placeholder alias is already in the file, so it has to go away again if the user leaves the template
    // without a name. This is what the in-place introduce refactorings do with what they introduced.
    // The renamer can replace the import element, so remember where it is instead of keeping a pointer to it.
    final int importOffset = importElement.getTextOffset();
    final String placeholder = sure(alias.getName());
    if (PythonUiService.getInstance().invokeInplaceRename(
      editor, alias, (brokenOff, named) -> removeAliasLater(project, editor, file, importOffset, placeholder, brokenOff, named))) {
      return;
    }
    // The in-place template could not be started (e.g. in-place rename is unavailable). Rather than leaving the
    // placeholder alias inserted above, ask for the name through a dialog and apply it as a regular rename.
    final String newName = PythonUiService.getInstance().showInputDialog(
      project,
      PyPsiBundle.message("INTN.add.import.alias.dialog.message", importedName),
      PyPsiBundle.message("INTN.add.import.alias.title"), placeholder,
      aliasInputValidator());
    final boolean named = newName != null && !newName.equals(placeholder);
    if (named) {
      PythonUiService.getInstance().runRenameProcessor(project, alias, newName, false, false);
    }
    removeUnnamedAlias(project, editor, file, importOffset, placeholder, newName == null, named);
  }

  /** Removes the alias once the template is gone, because the renamer still restores the placeholder name in the editor. */
  private static void removeAliasLater(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, int importOffset,
                                       @NotNull String placeholder, boolean brokenOff, boolean named) {
    ApplicationManager.getApplication().invokeLater(() -> {
      try (AccessToken ignore = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
        removeUnnamedAlias(project, editor, file, importOffset, placeholder, brokenOff, named);
      }
    }, project.getDisposed());
  }

  /**
   * Takes the placeholder alias out again when the user gave it no name. The user breaks the template off,
   * or ends it on the placeholder name, or the rename to the new name does not run. In each case the alias
   * still has the placeholder name, so a plain {@code import foo} would stay as {@code import foo as foo}.
   */
  private static void removeUnnamedAlias(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, int importOffset,
                                         @NotNull String placeholder, boolean brokenOff, boolean named) {
    if (!file.isValid()) {
      return;
    }
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    final IntentionState state =
      IntentionState.forImportElement(PsiTreeUtil.getParentOfType(file.findElementAt(importOffset), PyImportElement.class));
    final String alias = state.myAlias;
    if (state.myImportElement == null || alias == null) {
      return;
    }
    final PyReferenceExpression reference = state.myImportElement.getImportReferenceExpression();
    if (reference == null) {
      return;
    }
    final String importedName = PyPsiUtils.toPath(reference);
    // A rename to a new name that did not run, for example after a conflict, leaves the placeholder.
    // An alias that repeats the imported name says nothing, so drop it. A shorter name from a dotted
    // import, such as `import a.b.c as c`, is a result the user accepted and it stays.
    final boolean keptPlaceholder = alias.equals(placeholder);
    if (!brokenOff && !(keptPlaceholder && (named || alias.equals(importedName)))) {
      return;
    }
    CommandProcessor.getInstance().executeCommand(project, () -> {
      try {
        toggleAlias(project, state, importedName, alias, false);
      }
      catch (IncorrectOperationException ignored) {
        PythonUiService.getInstance().showBalloonWarning(project, PyPsiBundle.message("QFIX.action.failed"));
      }
    }, PyPsiBundle.message("INTN.NAME.toggle.import.alias"), null);
  }
}
