/*
 * User: anna
 * Date: 07-Mar-2008
 */
package com.jetbrains.python.actions;

import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInspection.HintAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyClassScopeProcessor;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddImportAction implements HintAction, QuestionAction, LocalQuickFix {
  private final PsiReference myReference;

  public AddImportAction(final PsiReference reference) {
    myReference = reference;
  }

  @NotNull
  public String getText() {
    return PyBundle.message("ACT.NAME.add.import");
  }

  @NotNull
  public String getName() {
    return getText();
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("ACT.FAMILY.import");
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    execute(descriptor.getPsiElement().getContainingFile());
  }

  @Nullable
  private String getRefName() {
    return getRefName(myReference);
  }

  @Nullable
  private static String getRefName(PsiReference ref) {
    return ((PyReferenceExpression)ref.getElement()).getReferencedName();
  }

  /**
   * Finds first import statement that imports given name. 
   */
  private static class ImportLookupProcessor implements PyClassScopeProcessor {

    String name;
    PsiElement found;

    private ImportLookupProcessor(String name) {
      this.name = name;
      this.found = null;
    }

    public boolean execute(PsiElement element, ResolveState state) {
      if (element instanceof PyImportElement) {
        PyImportElement imp = ((PyImportElement)element);
        PyReferenceExpression ref = imp.getImportReference();
        if (ref != null) {
          String refname = ref.getReferencedName();
          if (refname != null && refname.equals(name)) {
            found = element;
            return false;
          }
        }
      }
      return true;
    }

    public <T> T getHint(Key<T> hintKey) {
      return null;
    }

    public void handleEvent(Event event, Object associated) { }

    public PsiElement getFound() {
      return found;
    }

    @NotNull
    @Override
    public TokenSet getTargetTokenSet() {
      return PyResolveUtil.NAME_DEFINERS;
    }
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return hasSomethingToImport(file);
  }

  public boolean hasSomethingToImport(PsiFile file) {
    final PsiElement element = myReference.getElement();
    // not within import
    if (PsiTreeUtil.getParentOfType(element, PyImportStatement.class) != null) return false;
    if (PsiTreeUtil.getParentOfType(element, PyFromImportStatement.class) != null) return false;
    // don't propose to import unknown fields, etc qualified things
    if (element instanceof PyReferenceExpression) {
      final PyExpression qual = ((PyReferenceExpression)element).getQualifier();
      if (qual != null) return false;
    }
    // don't propose to import unimportable
    if (!(element instanceof PyReferenceExpression)) {
      return false;
    }
    final PsiElement resolveResult = ResolveImportUtil.resolvePythonImport2((PyReferenceExpression)element, null);
    if (resolveResult == null || resolveResult == element.getContainingFile()) {
      return false;
    }
    // don't propose to import what's already imported, under different name or unsuccessfully for any reason
    final String referenceName = getRefName();
    ImportLookupProcessor ilp = new ImportLookupProcessor(referenceName);
    PyResolveUtil.treeCrawlUp(ilp, element);
    if (ilp.getFound() != null) return false; // we found such an import already

    // see if there's something to import
    /*
    final PsiFile[] files = getRefFiles(referenceName);
    return files != null && files.length > 0;
    */
    return (ResolveImportUtil.resolveInRoots(file, referenceName) != null);
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    execute(file);
  }

  private void execute(final PsiFile file) {
    new WriteCommandAction(file.getProject(), file) {
      @Override
      protected void run(Result result) throws Throwable {
        String name = getRefName();
        final PsiElement element = ResolveImportUtil.resolveInRoots(file, name);
        if (element != null) { // TODO: think about multiple possible resole results
          final PsiFileSystemItem toImport = element instanceof PsiFileSystemItem ? (PsiFileSystemItem) element : element.getContainingFile();
          final AddImportHelper.ImportPriority priority = AddImportHelper.getImportPriority(file, toImport);
          AddImportHelper.addImportStatement(file, name, null, priority);
        }
      }
    }.execute();
  }

  public boolean startInWriteAction() {
    return false;
  }

  public boolean showHint(final Editor editor) {
    if (!PyCodeInsightSettings.getInstance().SHOW_IMPORT_POPUP) {
      return false;
    }
    if (!myReference.getElement().isValid() || isResolved(myReference)) {
      return false;
    }
    final String referenceName = getRefName();
    final PsiElement element = ResolveImportUtil.resolveInRoots(myReference.getElement(), referenceName);
    if (element == null || element == myReference.getElement().getContainingFile()) return false;
    String hintText = ShowAutoImportPass.getMessage(false, getRefName());
    HintManager.getInstance().showQuestionHint(editor, hintText, myReference.getElement().getTextOffset(),
                                               myReference.getElement().getTextRange().getEndOffset(), this);
    return true;
  }

  public static boolean isResolved(PsiReference reference) {
    if (reference instanceof PsiPolyVariantReference) {
      return ((PsiPolyVariantReference)reference).multiResolve(false).length > 0;
    }
    return reference.resolve() != null;
  }

  public boolean execute() {
    execute(myReference.getElement().getContainingFile());
    return true;
  }

}
