package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.*;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 08.02.2010
 * Time: 18:05:36
 */
public class PyUnsupportedFeaturesInspection extends LocalInspectionTool {
  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.unsupported.features");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "PyUnsupportedFeaturesInspection";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  static class Visitor extends PyInspectionVisitor {
    private static Set<String> REMOVED_METHODS = new HashSet<String>();

    static {
      REMOVED_METHODS.add("cmp");
      REMOVED_METHODS.add("apply");
      REMOVED_METHODS.add("callable");
      REMOVED_METHODS.add("coerce");
      REMOVED_METHODS.add("execfile");
      REMOVED_METHODS.add("reduce");
      REMOVED_METHODS.add("reload");
    }

    private static boolean isPy3K(PyElement node) {
      VirtualFile virtualFile = node.getContainingFile().getVirtualFile();
      return virtualFile != null && LanguageLevel.forFile(virtualFile).isPy3K();
    }

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyBinaryExpression(PyBinaryExpression node) {
      if (isPy3K(node)) {
        if (node.isOperator("<>")) {
          registerProblem(node, "<> is not supported in Python 3, use != instead", new ReplaceNotEqOperatorQuickFix());
        }
      }
    }

    @Override
    public void visitPyNumericLiteralExpression(PyNumericLiteralExpression node) {
      if (isPy3K(node)) {
        String text = node.getText();
        if (text.endsWith("l") || text.endsWith("L")) {
          registerProblem(node, "Integer literals do not support a trailing \'l\' or \'L\' in Python 3", new RemoveTrailingLQuickFix());
        }
        if (text.charAt(0) == '0' && (text.charAt(1) != 'o' || text.charAt(1) != 'b')) {
          registerProblem(node, "Python 3 requires '0o' prefix for octal literals", new ReplaceOctalNumericLiteralQuickFix());
        }
      }
    }

    @Override
    public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
      if (isPy3K(node)) {
        String text = node.getText();
        if (text.startsWith("u") || text.startsWith("U")) {
          registerProblem(node, "String literals do not support a leading \'u\' or \'U\' in Python 3", new ReamoveLeadingUQuickFix());
        }
      }
    }


    @Override
    public void visitPyListCompExpression(PyListCompExpression node) {
      List<ComprhForComponent> forComponents = node.getForComponents();
      for (ComprhForComponent forComponent: forComponents) {
        PyExpression iteratedList = forComponent.getIteratedList();
        if (iteratedList instanceof PyTupleExpression) {
          registerProblem(iteratedList, "List comprehensions do not support such syntax in Python 3", new ReplaceListComprehensionsQuickFix());
        }
      }
    }

    @Override
    public void visitPyExceptBlock(PyExceptPart node) {
      PyExpression exceptClass = node.getExceptClass();
      if (exceptClass != null && node.getTarget() != null) {
        if (isPy3K(node)) {
          PsiElement element = exceptClass.getNextSibling();
          while (element instanceof PsiWhiteSpace) {
            element = element.getNextSibling();
          }
          if (element != null && ",".equals(element.getText())) {
            registerProblem(node, "Python 3 does not support this syntax", new ReplaceExceptPartQuickFix(true));
          }
        }
      }
    }

    @Override
    public void visitPyRaiseStatement(PyRaiseStatement node) {
      PyExpression[] expressions = node.getExpressions();
      assert (expressions != null);
      if (expressions.length < 2) {
        return;
      }

      if (isPy3K(node)) {
        if (expressions.length == 3) {
          registerProblem(node, "Python 3 does not support this syntax", new ReplaceRaiseStatementQuickFix());
          return;
        }
        PsiElement element = expressions[0].getNextSibling();
        while (element instanceof PsiWhiteSpace) {
          element = element.getNextSibling();
        }
        if (element != null && ",".equals(element.getText())) {
          registerProblem(node, "Python 3 does not support this syntax", new ReplaceRaiseStatementQuickFix());
        }
      }
    }

    @Override
    public void visitPyReprExpression(PyReprExpression node) {
      if (isPy3K(node)) {
        registerProblem(node, "Backquote is not supported in Python 3, use repr() instead", new ReplaceBackquoteExpressionQuickFix());
      }
    }

    @Override
    public void visitPyImportStatement(PyImportStatement node) {
      if (isPy3K(node)) {
        PyImportElement[] importElements = node.getImportElements();
        for (PyImportElement importElement : importElements) {
          PyReferenceExpression importReference = importElement.getImportReference();
          if (importReference != null) {
            String name = importReference.getName();
            if ("__builtin__".equals(name)) {
              registerProblem(node, "Module __builtin__ renamed to builtins");
            }
          }
        }
      }
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      if (isPy3K(node)) {
        String name = node.getCallee().getName();
        if ("raw_input".equals(name)) {
          registerProblem(node.getCallee(), PyBundle.message("INSP.method.$0.removed.use.$1", name, "input"),
                          new ReplaceMethodQuickFix("input"));
        } else if (REMOVED_METHODS.contains(name)) {
          registerProblem(node.getCallee(), PyBundle.message("INSP.method.$0.removed", name));
        }
      }
    }
  }
}
