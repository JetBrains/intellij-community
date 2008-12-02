package com.jetbrains.python.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParameter;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Map;

/**
 * Looks at argument lists.
 * User: dcheryasov
 * Date: Nov 14, 2008
 */
public class PyArgumentListInspection  extends LocalInspectionTool {
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.incorrect.call.arguments");
  }

  @NotNull
  public String getShortName() {
    return "PyArgumentListInspection";
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
    public void visitPyArgumentList(final PyArgumentList node) {
      // analyze
      /*ArgumentAnalysisResult result = analyzeArgumentList(node);*/
      PyArgumentList.AnalysisResult result = node.analyzeCall();
      /*
      // X XX debug
      PyCallExpression call_ex = node.getCallExpression();
      System.out.println(PyResolveUtil.getReadableRepr(call_ex));
      for (Map.Entry<PyExpression, PyParameter> entry : result.getPlainMappedParams().entrySet()) {
        System.out.println(
          PyResolveUtil.getReadableRepr(entry.getValue()) +
          " -> " + PyResolveUtil.getReadableRepr(entry.getKey())
      PyCallExpression call_ex = node.getCallExpression();
      System.out.println(PyResolveUtil.getReadableRepr(call_ex));
      for (Map.Entry<PyExpression, PyParameter> entry : result.getPlainMappedParams().entrySet()) {
        System.out.println(
          PyResolveUtil.getReadableRepr(entry.getValue()) +
          " -> " + PyResolveUtil.getReadableRepr(entry.getKey())
        );
      }
      for (PyExpression arg : result.getTupleMappedParams()) {
        System.out.println(PyResolveUtil.getReadableRepr(arg) + " -> *");
      }
      for (PyExpression arg : result.getKwdMappedParams()) {
        System.out.println(PyResolveUtil.getReadableRepr(arg) + " -> **");
      }
      System.out.println("Tuple arg " + PyResolveUtil.getReadableRepr(result.getTupleArg()));
      System.out.println("Kwd arg " + PyResolveUtil.getReadableRepr(result.getKwdArg()));
      System.out.println();

        );
      }
      for (PyExpression arg : result.getTupleMappedParams()) {
        System.out.println(PyResolveUtil.getReadableRepr(arg) + " -> *");
      }
      for (PyExpression arg : result.getKwdMappedParams()) {
        System.out.println(PyResolveUtil.getReadableRepr(arg) + " -> **");
      }
      System.out.println("Tuple arg " + PyResolveUtil.getReadableRepr(result.getTupleArg()));
      System.out.println("Kwd arg " + PyResolveUtil.getReadableRepr(result.getKwdArg()));
      System.out.println();
      // \\
      */
      // show argument problems
      for (Map.Entry<PyExpression, EnumSet<PyArgumentList.ArgFlag>> arg_entry : result.getArgumentFlags().entrySet()) {
        EnumSet<PyArgumentList.ArgFlag> flags = arg_entry.getValue();
        if (!flags.isEmpty()) { // something's wrong
          PyExpression arg = arg_entry.getKey();
          if (flags.contains(PyArgumentList.ArgFlag.IS_DUP)) {
            registerProblem(arg, PyBundle.message("INSP.duplicate.argument"));
          }
          if (flags.contains(PyArgumentList.ArgFlag.IS_DUP_KWD)) {
            registerProblem(arg, PyBundle.message("INSP.duplicate.doublestar.arg"));
          }
          if (flags.contains(PyArgumentList.ArgFlag.IS_DUP_TUPLE)) {
            registerProblem(arg, PyBundle.message("INSP.duplicate.star.arg"));
          }
          if (flags.contains(PyArgumentList.ArgFlag.IS_POS_PAST_KWD)) {
            registerProblem(arg, PyBundle.message("INSP.cannot.appear.past.keyword.arg"));
          }
          if (flags.contains(PyArgumentList.ArgFlag.IS_UNMAPPED)) {
            registerProblem(arg, PyBundle.message("INSP.unexpected.arg"));
          }
        }
      }
      // show unfilled params
      ASTNode our_node = node.getNode();
      if (our_node != null) {
        ASTNode close_paren = our_node.findChildByType(PyTokenTypes.RPAR);
        if (close_paren != null) {
          for (PyParameter param : result.getUnmappedParams()) {
            registerProblem(close_paren.getPsi(), PyBundle.message("INSP.parameter.$0.unfilled", param.getName()));
          }
        }
      }
    }
  }

}
