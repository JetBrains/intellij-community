package com.jetbrains.python.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyNoneType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.actions.AddImportAction;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Marks references that fail to resolve.
 * User: dcheryasov
 * Date: Nov 15, 2008
 */
public class PyUnresolvedReferencesInspection extends LocalInspectionTool {
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.unresolved.refs");
  }

  @NotNull
  public String getShortName() {
    return "PyUnresolvedReferencesInspection";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new Visitor(holder);
  }

  public static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyElement(final PyElement node) {
      super.visitPyElement(node);    //To change body of overridden methods use File | Settings | File Templates.
      for (final PsiReference reference : node.getReferences()) {
        if (reference.isSoft()) continue;
        HighlightSeverity severity = HighlightSeverity.ERROR;
        if (reference instanceof PsiReferenceEx) {
          severity = ((PsiReferenceEx) reference).getUnresolvedHighlightSeverity();
          if (severity == null) continue;
        }
        if (reference.resolve() == null) {
          StringBuffer description_buf = new StringBuffer("");
          String text = reference.getElement().getText();
          String ref_text = reference.getRangeInElement().substring(text); // text of the part we're working with
          LocalQuickFix action = null;
          if (ref_text.length() <= 0) return; // empty text, nothing to highlight
          if (reference instanceof PyReferenceExpression) {
            PyReferenceExpression refex = (PyReferenceExpression)reference;
            String refname = refex.getReferencedName();
            if (refex.getQualifier() != null) {
              final PyClassType object_type = PyBuiltinCache.getInstance(node.getProject()).getObjectType();
              if ((object_type != null) && object_type.getPossibleInstanceMembers().contains(refname)) continue;
            }
            // unqualified:
            // may be module's
            if ((new PyModuleType(node.getContainingFile())).getPossibleInstanceMembers().contains(refname)) continue;
            // may be try: import; not an error not to resolve
            if ((PsiTreeUtil.getParentOfType(
                PsiTreeUtil.getParentOfType(node, PyImportElement.class), PyTryExceptStatement.class, PyIfStatement.class) != null)
            ) {
              severity = HighlightSeverity.INFO;
              String errmsg = PyBundle.message("INSP.module.$0.not.found", ref_text);
              description_buf.append(errmsg);
            }
          }
          if (reference instanceof PsiReferenceEx) {
            final String s = ((PsiReferenceEx)reference).getUnresolvedDescription();
            if (s != null) description_buf.append(s);
          }
          if (description_buf.length() == 0) {
            boolean marked_for_class = false;
            if (reference instanceof PyQualifiedExpression) {
              final PyExpression qexpr = ((PyQualifiedExpression)reference).getQualifier();
              if (qexpr != null) {
                PyType qtype = qexpr.getType();
                if (qtype != null) {
                  if (qtype instanceof PyNoneType) {
                    // this almost always means that we don't know the type, so don't show an error in this case
                    continue;
                  }
                  description_buf.append(PyBundle.message("INSP.unresolved.ref.$0.for.class.$1", ref_text, qtype.getName()));
                  marked_for_class = true;
                }
              }
            }
            if (! marked_for_class) {
              description_buf.append(PyBundle.message("INSP.unresolved.ref.$0", ref_text));
              action = new AddImportAction(reference);
            }
          }
          String description = description_buf.toString();
          ProblemHighlightType hl_type;
          if (severity == HighlightSeverity.WARNING) {
            hl_type = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
          }
          else {
            hl_type = ProblemHighlightType.LIKE_UNKNOWN_SYMBOL;
          }
          PsiElement point = node.getLastChild(); // usually the identifier at the end of qual ref
          if (point == null) point = node;
          registerProblem(/*reference.getElement()*/ point, description, hl_type, null, action);
        }
      }
    }

  }
}
