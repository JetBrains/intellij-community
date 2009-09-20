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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveState;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyClassScopeProcessor;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddImportAction implements HintAction, QuestionAction, LocalQuickFix {
  private final PsiReference myReference;
  // private final Project myProject;

  public AddImportAction(final PsiReference reference) {
    myReference = reference;
    // myProject = reference.getElement().getProject();
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
    return ((PyReferenceExpression)ref).getReferencedName();
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
    public Class[] getPossibleTargets() {
      return NAME_DEFINER_ONLY;
    }
  }

  /*
  private PsiFile[] getRefFiles(final String referenceName) {
    return getRefFiles(referenceName, myProject);
  }

  private static PsiFile[] getRefFiles(final String referenceName, Project project) {
    PsiFile[] files = FilenameIndex.getFilesByName(project, referenceName + DOT_PY, GlobalSearchScope.allScope(project));
    if (files == null) files = PsiFile.EMPTY_ARRAY;
    return files;
  }
  */

  /**
   * @param ref a reference to something potentially importable.
   * @return true if the referred name can actually be made resolvable by adding an import statement.
   */
  public static boolean isAvailableAt(PsiReference ref) {
    if (ref == null) return false;
    final PsiElement element = ref.getElement();
    // not within import
    if (PsiTreeUtil.getParentOfType(element, PyImportStatement.class) != null) return false;
    if (PsiTreeUtil.getParentOfType(element, PyFromImportStatement.class) != null) return false;
    // don't propose to import unknown fields, etc qualified things
    if (ref instanceof PyReferenceExpression) {
      final PyExpression qual = ((PyReferenceExpression)ref).getQualifier();
      if (qual != null) return false;
    }
    // don't propose to import unimportable
    if (
      !(ref instanceof PyReferenceExpression)  ||
      (ResolveImportUtil.resolvePythonImport2((PyReferenceExpression)ref, null) == null)
    ) return false;

    final String referenceName = getRefName(ref);
    /*
    // don't propose to import what's already imported, under different name or unsuccessfully for any reason
    ImportLookupProcessor ilp = new ImportLookupProcessor(referenceName);
    PyResolveUtil.treeCrawlUp(ilp, element);
    if (ilp.getFound() != null) return false; // we found such an import already
    */

    // see if there's something to import
    return (ResolveImportUtil.resolveInRoots(element, referenceName) != null);
  }

  /**
   * @param ref supposed reference to a module
   * @return true if a module with the referenced name is already imported, maybe under a different alias.
   */
  public static boolean isAlreadyImportedDifferently(PsiReference ref) {
    if (ref == null) return false;
    final PsiElement element = ref.getElement();
    final String referenceName = getRefName(ref);
    ImportLookupProcessor ilp = new ImportLookupProcessor(referenceName);
    PyResolveUtil.treeCrawlUp(ilp, element);
    return (ilp.getFound() != null);
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    final PsiElement element = myReference.getElement();
    // not within import
    if (PsiTreeUtil.getParentOfType(element, PyImportStatement.class) != null) return false;
    if (PsiTreeUtil.getParentOfType(element, PyFromImportStatement.class) != null) return false;
    // don't propose to import unknown fields, etc qualified things
    if (myReference instanceof PyReferenceExpression) {
      final PyExpression qual = ((PyReferenceExpression)myReference).getQualifier();
      if (qual != null) return false;
    }
    // don't propose to import unimportable
    if (
      !(myReference instanceof PyReferenceExpression)  ||
      (ResolveImportUtil.resolvePythonImport2((PyReferenceExpression)myReference, null) == null)
    ) return false;
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
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        String name = getRefName();
        if (ResolveImportUtil.resolveInRoots(file, name) != null) { // TODO: think about multiple possible resole results
          AddImportHelper.addImportStatement(file, name, null, file.getProject());
        }
      }
    });
  }

  public boolean startInWriteAction() {
    return false;
  }

  public boolean showHint(final Editor editor) {
    final String referenceName = getRefName();
    /*
    final PsiFile[] files = getRefFiles(referenceName);
    if (!(files != null && files.length > 0)) return false;
    */
    if (ResolveImportUtil.resolveInRoots(myReference.getElement(), referenceName) == null) return false;
    String hintText = ShowAutoImportPass.getMessage(false, getRefName());
    HintManager.getInstance().showQuestionHint(editor, hintText, myReference.getElement().getTextOffset(),
                                               myReference.getElement().getTextRange().getEndOffset(), this);
    return true;
  }

  public boolean execute() {
    execute(myReference.getElement().getContainingFile());
    return true;
  }

}
