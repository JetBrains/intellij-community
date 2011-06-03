package com.jetbrains.python.actions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.facet.FacetFinder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.django.facet.DjangoFacet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.psi.PyUtil.sure;

/**
 * Adds a missing top-level function to a module.
 * <br/>
 * User: dcheryasov
 * Date: Sep 15, 2010 4:34:23 PM
 * @see AddMethodQuickFix AddMethodQuickFix
 */
public class AddFunctionQuickFix  implements LocalQuickFix {

  private String myIdentifier;
  private PyFile myPyFile;

  public AddFunctionQuickFix(String identifier, PyFile module) {
    myIdentifier = identifier;
    myPyFile = module;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.NAME.add.function.$0.to.module.$1", myIdentifier, myPyFile.getName());
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    try {
      // descriptor points to the unresolved identifier
      // there can be no name clash, else the name would have resolved, and it hasn't.
      PsiElement problem_elt = descriptor.getPsiElement().getParent(); // id -> ref expr
      String item_name = myIdentifier;
      sure(myPyFile); sure(item_name);
      sure(CodeInsightUtilBase.preparePsiElementForWrite(myPyFile));
      // try to at least match parameter count
      // TODO: get parameter style from code style
      PyFunctionBuilder builder = new PyFunctionBuilder(item_name);
      PsiElement problem_parent = problem_elt.getParent();
      if (problem_parent instanceof PyCallExpression) {
        PyArgumentList arglist = ((PyCallExpression)problem_parent).getArgumentList();
        sure(arglist);
        final PyExpression[] args = arglist.getArguments();
        for (PyExpression arg : args) {
          if (arg instanceof PyKeywordArgument) { // foo(bar) -> def foo(bar_1)
            builder.parameter(((PyKeywordArgument)arg).getKeyword());
          }
          else if (arg instanceof PyReferenceExpression) {
            PyReferenceExpression refex = (PyReferenceExpression)arg;
            builder.parameter(refex.getReferencedName());
          }
          else { // use a boring name
            builder.parameter("param");
          }
        }
      }
      else if (problem_parent != null) {
        PsiFile source_file = problem_parent.getContainingFile();
        if (source_file != null && "urls.py".equals(source_file.getName()) && DjangoFacet.isPresent(source_file)) {
          builder.parameter("request"); // specifically for mentions in urlpatterns
        }
      }
      // else: no arglist, use empty args
      PyFunction function = builder.buildFunction(project, LanguageLevel.forFile(myPyFile.getVirtualFile()));

      // add to the bottom
      function = (PyFunction) myPyFile.add(function);
      showTemplateBuilder(function);
    }
    catch (IncorrectOperationException ignored) {
      // we failed. tell about this
      PyUtil.showBalloon(project, PyBundle.message("QFIX.failed.to.add.function"), MessageType.ERROR);
    }
  }

  private static void showTemplateBuilder(PyFunction method) {
    method = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(method);

    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(method);
    ParamHelper.walkDownParamArray(
      method.getParameterList().getParameters(),
      new ParamHelper.ParamVisitor() {
        public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
          builder.replaceElement(param, param.getName());
        }
      }
    );

    // TODO: detect expected return type from call site context: PY-1863
    builder.replaceElement(method.getStatementList(), "return None");

    builder.run();
  }
}
