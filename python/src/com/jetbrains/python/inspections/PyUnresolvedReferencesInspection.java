package com.jetbrains.python.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.validation.AddImportAction;
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
    return "Python"; // TODO: propertize
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Unresolved Python reference"; // TODO: propertize
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
    return HighlightDisplayLevel.ERROR;
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
              severity = HighlightSeverity.WARNING;
              description_buf.append("Module '").append(reference.getRangeInElement().substring(text)).append("' not found");
            }
          }
          if (reference instanceof PsiReferenceEx) {
            final String s = ((PsiReferenceEx)reference).getUnresolvedDescription();
            if (s != null) description_buf.append(s);
          }
          if (description_buf.length() == 0) {
            description_buf.append("Unresolved reference '").append(reference.getRangeInElement().substring(text)).append("'");
            if (reference instanceof PyQualifiedExpression) {
              final PyExpression qexpr = ((PyQualifiedExpression)reference).getQualifier();
              if (qexpr != null) {
                PyType qtype = qexpr.getType();
                if (qtype != null) {
                  description_buf.append(" for class ").append(qtype.getName());
                }
              }
            }
          }
          String description = description_buf.toString();
          ProblemHighlightType hl_type;
          //final TextRange highlightRange = reference.getRangeInElement().shiftRight(reference.getElement().getTextRange().getStartOffset());
          if (severity == HighlightSeverity.WARNING) {
            //annotation = getHolder().createWarningAnnotation(highlightRange, description_buf.toString());
            hl_type = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
          }
          else {
            //annotation = getHolder().createErrorAnnotation(highlightRange, description_buf.toString());
            //annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
            hl_type = ProblemHighlightType.LIKE_UNKNOWN_SYMBOL;
          }
          registerProblem(reference.getElement(), description, hl_type, new AddImportAction(reference));
        }
      }
    }

  }
}
