package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Map;

/**
 * Looks at argument lists.
 * @author dcheryasov
 */
public class PyArgumentListInspection extends PyInspection {
  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.incorrect.call.arguments");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder, LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyArgumentList(final PyArgumentList node) {
      // analyze
      inspectPyArgumentList(node, getHolder(), myTypeEvalContext);
    }

    @Override
    public void visitPyDecoratorList(final PyDecoratorList node) {
      PyDecorator[] decos = node.getDecorators();
      for (PyDecorator deco : decos) {
        if (! deco.hasArgumentList()) {
          // empty arglist; deco function must have a non-kwarg first arg
          PyCallExpression.PyMarkedCallee mkfunc = deco.resolveCallee(myTypeEvalContext);
          if (mkfunc != null && !mkfunc.isImplicitlyResolved()) {
            Callable callable = mkfunc.getCallable();
            int first_param_offset =  mkfunc.getImplicitOffset();
            PyParameter[] params = callable.getParameterList().getParameters();
            PyNamedParameter alleged_first_param = params.length < first_param_offset ? null : params[first_param_offset-1].getAsNamed();
            if (alleged_first_param == null || alleged_first_param.isKeywordContainer()) {
              // no parameters left to pass function implicitly, or wrong param type
              registerProblem(deco, PyBundle.message("INSP.func.$0.lacks.first.arg", callable.getName())); // TODO: better names for anon lambdas
            }
            else {
              // possible unfilled params
              for (int i=first_param_offset; i < params.length; i += 1) {
                PyNamedParameter par = params[i].getAsNamed();
                // param tuples, non-starred or non-default won't do
                if (par == null || (! par.isKeywordContainer() && ! par.isPositionalContainer() && !par.hasDefaultValue())) {
                  String par_name;
                  if (par != null) par_name = par.getName();
                  else par_name = "(...)"; // can't be bothered to find the first non-tuple inside it
                  registerProblem(deco, PyBundle.message("INSP.parameter.$0.unfilled", par_name));
                }
              }
            }
          }
        }
        // else: this case is handled by arglist visitor
      }
    }

  }

  public static void inspectPyArgumentList(PyArgumentList node, ProblemsHolder holder, final TypeEvalContext context) {
    if (node.getParent() instanceof PyClass) return; // class Foo(object) is also an arg list
    PyArgumentList.AnalysisResult result = node.analyzeCall(context);
    if (!result.isImplicitlyResolved()) {
      for (Map.Entry<PyExpression, EnumSet<PyArgumentList.ArgFlag>> arg_entry : result.getArgumentFlags().entrySet()) {
        EnumSet<PyArgumentList.ArgFlag> flags = arg_entry.getValue();
        if (!flags.isEmpty()) { // something's wrong
          PyExpression arg = arg_entry.getKey();
          if (flags.contains(PyArgumentList.ArgFlag.IS_DUP)) {
            holder.registerProblem(arg, PyBundle.message("INSP.duplicate.argument"));
          }
          if (flags.contains(PyArgumentList.ArgFlag.IS_DUP_KWD)) {
            holder.registerProblem(arg, PyBundle.message("INSP.duplicate.doublestar.arg"));
          }
          if (flags.contains(PyArgumentList.ArgFlag.IS_DUP_TUPLE)) {
            holder.registerProblem(arg, PyBundle.message("INSP.duplicate.star.arg"));
          }
          if (flags.contains(PyArgumentList.ArgFlag.IS_POS_PAST_KWD)) {
            holder.registerProblem(arg, PyBundle.message("INSP.cannot.appear.past.keyword.arg"));
          }
          if (flags.contains(PyArgumentList.ArgFlag.IS_UNMAPPED)) {
            holder.registerProblem(arg, PyBundle.message("INSP.unexpected.arg"));
          }
          if (flags.contains(PyArgumentList.ArgFlag.IS_TOO_LONG)) {
            holder.registerProblem(arg, PyBundle.message("INSP.more.args.that.pos.params"));
          }
        }
      }
      // show unfilled params
      ASTNode our_node = node.getNode();
      if (our_node != null) {
        ASTNode close_paren = our_node.findChildByType(PyTokenTypes.RPAR);
        if (close_paren != null) {
          for (PyNamedParameter param : result.getUnmappedParams()) {
            holder.registerProblem(close_paren.getPsi(), PyBundle.message("INSP.parameter.$0.unfilled", param.getName()));
          }
        }
      }
    }
    // syntax of *args
    for (PyExpression arg : node.getArguments()) {
      if (arg instanceof PyStarArgument) {
        PyExpression content = PyUtil.peelArgument(PsiTreeUtil.findChildOfType(arg, PyExpression.class));
        if (content != null) {
          PyType inside_type = context.getType(content);
          if (inside_type != null && !(inside_type instanceof PyTypeReference)) {
            if (((PyStarArgument)arg).isKeyword()) {
              if (! isMappingType(inside_type)) {
                holder.registerProblem(arg, PyBundle.message("INSP.expected.dict.got.$0", inside_type.getName()));
              }
            }
            else { // * arg
              if (! isSequenceType(inside_type)) {
                holder.registerProblem(arg, PyBundle.message("INSP.expected.seq.got.$0", inside_type.getName()));
              }
            }
          }
        }
      }
    }
    /*
    // did we succeed at all?
    if (result.getMarkedCallee() == null) {
      PsiElement marked = node;
      while (marked != null && marked.getTextRange().isEmpty()) marked = marked.getParent();
      if (marked != null) holder.registerProblem(node, PyBundle.message("INSP.cannot.analyze"), ProblemHighlightType.INFO);
    }
    */
  }

  private static boolean isSequenceType(PyType a_type) {
    if (a_type instanceof PyTupleType) return true;
    if ("list".equals(a_type.getName()) && a_type.isBuiltin()) return true;
    if (a_type instanceof PyClassType) {
      final PyClass cls = ((PyClassType)a_type).getPyClass();
      if (cls != null && cls.findMethodByName("__getitem__", true) != null) return true;
    }
    return false;
  }

  private static boolean isMappingType(PyType a_type) {
    // TODO: when we have proper support for ABCs, we could use an interface conformance check here
    if ("dict".equals(a_type.getName()) && a_type.isBuiltin()) return true;
    if (a_type instanceof PyClassType) {
      final PyClass cls = ((PyClassType)a_type).getPyClass();
      if (cls != null && cls.findMethodByName("__getitem__", true) != null  && cls.findMethodByName("keys", true) != null) return true;
    }
    return false;
  }

}
