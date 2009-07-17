/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PyArgumentListImpl extends PyElementImpl implements PyArgumentList {
  public PyArgumentListImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyArgumentList(this);
  }

  @NotNull
  @PsiCached
  public PyExpression[] getArguments() {
    return childrenToPsi(PyElementTypes.EXPRESSIONS, PyExpression.EMPTY_ARRAY);
  }

  @Nullable
  @PsiCached
  public PyKeywordArgument getKeywordArgument(String name) {
    ASTNode node = getNode().getFirstChildNode();
    while (node != null) {
      if (node.getElementType() == PyElementTypes.KEYWORD_ARGUMENT_EXPRESSION) {
        PyKeywordArgument arg = (PyKeywordArgument)node.getPsi();
        String keyword = arg.getKeyword();
        if (keyword != null && keyword.equals(name)) return arg;
      }
      node = node.getTreeNext();
    }
    return null;
  }

  public void addArgument(PyExpression arg) {
    PyUtil.ensureWritable(this);
    // it should find the comma after the argument to add after, and add after
    // that. otherwise it won't deal with comments nicely
    if (arg instanceof PyKeywordArgument) {
      PyKeywordArgument keywordArgument = (PyKeywordArgument)arg;
      PyKeywordArgument lastKeyArg = null;
      PyExpression firstNonKeyArg = null;
      for (PsiElement element : getChildren()) {
        if (element instanceof PyKeywordArgument) {
          lastKeyArg = (PyKeywordArgument)element;
        }
        else if (element instanceof PyExpression && firstNonKeyArg == null) {
          firstNonKeyArg = (PyExpression)element;
        }
      }
      if (lastKeyArg != null) {
        // add after last key arg
        addArgumentNode(keywordArgument, lastKeyArg.getNode().getTreeNext(), true);

      }
      else if (firstNonKeyArg != null) {
        // add before first non key arg
        addArgumentNode(keywordArgument, firstNonKeyArg.getNode(), true);

      }
      else {
        // add as only argument
        addArgumentLastWithoutComma(arg);
      }
    }
    else {
      addArgumentLastWithoutComma(arg);
    }
  }

  public void addArgumentFirst(PyExpression arg) {
    PyUtil.ensureWritable(this);
    ASTNode node = getNode();
    ASTNode[] pars = node.getChildren(TokenSet.create(PyTokenTypes.LPAR));
    if (pars.length == 0) {
      // there's no starting paren
      try {
        add(arg);
      }
      catch (IncorrectOperationException e1) {
        throw new IllegalStateException(e1);
      }

    }
    else {
      ASTNode before = PyUtil.getNextNonWhitespace(pars[0]);
      ASTNode anchorBefore;
      if (before != null && elementPrecedesElementsOfType(before, PyElementTypes.EXPRESSIONS)) {
        ASTNode comma = getLanguage().getElementGenerator().createComma(getProject());
        node.addChild(comma, before);
        anchorBefore = comma;
      }
      else {
        anchorBefore = before;
      }
      ASTNode argNode = arg.getNode();
      if (anchorBefore == null) {
        node.addChild(argNode);
      }
      else {
        node.addChild(argNode, anchorBefore);
      }
    }
  }

  private static boolean elementPrecedesElementsOfType(ASTNode before, TokenSet expressions) {
    ASTNode node = before;
    while (node != null) {
      if (expressions.contains(node.getElementType())) return true;
      node = node.getTreeNext();
    }
    return false;
  }

  private void addArgumentLastWithoutComma(PyExpression arg) {
    PyUtil.ensureWritable(this);
    ASTNode node = getNode();
    ASTNode[] pars = node.getChildren(TokenSet.create(PyTokenTypes.RPAR));
    if (pars.length == 0) {
      // there's no ending paren
      try {
        add(arg);
      }
      catch (IncorrectOperationException e1) {
        throw new IllegalStateException(e1);
      }

    }
    else {
      node.addChild(arg.getNode(), pars[pars.length - 1]);
    }
  }

  private void addArgumentNode(PyExpression arg, ASTNode beforeThis, boolean commaFirst) {
    PyUtil.ensureWritable(this);
    ASTNode comma = getLanguage().getElementGenerator().createComma(getProject());
    ASTNode node = getNode();
    ASTNode argNode = arg.getNode();
    if (commaFirst) {
      node.addChild(comma, beforeThis);
      node.addChild(argNode, beforeThis);
    }
    else {
      node.addChild(argNode, beforeThis);
      node.addChild(comma, beforeThis);
    }
  }

  public void addArgumentAfter(PyExpression argument, @Nullable PyExpression afterThis) {
    PyUtil.ensureWritable(this);
    if (afterThis == null) {
      addArgumentFirst(argument);
      return;
    }
    boolean good = false;
    for (PyExpression expression : getArguments()) {
      if (expression == afterThis) {
        good = true;
        break;
      }
    }
    if (!good) {
      throw new IllegalArgumentException("Expression " + afterThis + " is not an argument (" + Arrays.toString(getArguments()) + ")");
    }
    // CASES:
    ASTNode node = afterThis.getNode().getTreeNext();
    while (node != null) {
      IElementType type = node.getElementType();
      if (type == PyTokenTypes.RPAR) {
        // 1: Nothing, just add
        addArgumentNode(argument, node, true);
        break;

      }
      else if (PyElementTypes.EXPRESSIONS.contains(type)) {
        // 2: After some argument followed by comma: after comma, add element, add comma
        // 3: After some argument not followed by comma: add comma, add element
        addArgumentNode(argument, node, true);
        break;

      }
      else if (type == PyTokenTypes.COMMA) {
        ASTNode next = PyUtil.getNextNonWhitespace(node);
        if (next == null) {
          addArgumentLastWithoutComma(argument);
        }
        else if (next.getElementType() == PyTokenTypes.RPAR) {
          addArgumentNode(argument, next, false);
        }
        else {
          addArgumentNode(argument, next, false);
        }
        break;
      }
      node = node.getTreeNext();
    }
  }

  @Nullable
  public PyCallExpression getCallExpression() {
    return PsiTreeUtil.getParentOfType(this, PyCallExpression.class);
  }

  @SuppressWarnings({"SuspiciousMethodCalls"})
  protected void deletePyChild(PyBaseElementImpl element) throws IncorrectOperationException {
    if (Arrays.asList(getArguments()).contains(element)) {
      ASTNode node = element.getNode();
      ASTNode next = PyPsiUtils.getNextComma(node);
      if (next == null) {
        next = PyPsiUtils.getPrevComma(node);
      }
      ASTNode me = getNode();
      if (next != null) {
        me.removeChild(next);
      }
      me.removeChild(node);
    }
  }

  public AnalysisResult analyzeCall() {
    final AnalysisResultImpl ret = new AnalysisResultImpl();
    PyExpression[] arguments = getArguments();
    // declaration-based checks
    // proper arglist is: [positional,...][name=value,...][*tuple,][**dict]
    // where "positional" may be a tuple of nested "positional" parameters, too.
    // following the spec: http://docs.python.org/ref/calls.html
    PyCallExpression call = getCallExpression();
    if (call != null) {
      PyCallExpression.PyMarkedFunction resolved_callee = call.resolveCallee();
      ret.my_marked_func = resolved_callee;
      if (resolved_callee != null) {
        PyFunction func = resolved_callee.getFunction();
        PyParameterList paramlist = func.getParameterList();
        PyParameter[] params = paramlist.getParameters();
        // prepare args and slots
        List<PyExpression> unmatched_args = new LinkedList<PyExpression>();
        Collections.addAll(unmatched_args, arguments);
        Map<String, PyExpression> param_slots = new HashMap<String, PyExpression>();
        PyNamedParameter kwd_slot = null; // the *tuple. might be just boolean, but this way debugging is easier
        PyNamedParameter tuple_slot = null; // the **kwd
        PyStarArgument kwd_arg = null;
        PyStarArgument tuple_arg = null;
        final List<PyExpression> unmatched_subargs = new LinkedList<PyExpression>(); // unmatched nested arguments will go here
        // all slots are initially empty, *x and **x are not among slots
        for (PyParameter a_param : params) {
          PyNamedParameter n_param = a_param.getAsNamed();
          if (n_param != null) {
            if (n_param.isPositionalContainer()) tuple_slot = n_param;
            else if (n_param.isKeywordContainer()) kwd_slot = n_param;
            else param_slots.put(a_param.getName(), null);
          }
        }
        // look for star args
        for (PyExpression arg : arguments) {
          if (arg instanceof PyStarArgument) {
            final PyStarArgument star_arg = (PyStarArgument)arg;
            if (star_arg.isKeyword()) {
              if (kwd_arg == null) kwd_arg = star_arg;
              else {
                ret.markArgument(arg, ArgFlag.IS_DUP_KWD);
                //getHolder().createErrorAnnotation(arg, "duplicate **arg");
                unmatched_args.remove(arg); // error. ignore later
              }
            }
            else {
              if (tuple_arg == null) {
                tuple_arg = star_arg;
              }
              else {
                ret.markArgument(arg, ArgFlag.IS_DUP_TUPLE);
                //getHolder().createErrorAnnotation(arg, "duplicate *arg");
                unmatched_args.remove(arg); // error. ignore later
              }
            }
          }
        }
        // rule out 'self' or other implicit params
        int param_index = 0;
        for (int i=0; i < resolved_callee.getImplicitOffset() && i < params.length; i+=1) {
          param_slots.remove(params[i].getName()); // the self param
          param_index += 1;
        }
        boolean seen_tuple_arg = false;
        boolean seen_kwd_arg = false;
        ListIterator<PyExpression> unmatched_arg_iter = unmatched_args.listIterator();
        // check positional args
        while (unmatched_arg_iter.hasNext() && (param_index < params.length)) {
          final PyExpression arg = unmatched_arg_iter.next(); // current arg
          PyParameter a_param = params[param_index];      // its matching param
          PyNamedParameter n_param = a_param.getAsNamed();
          if (n_param != null) { // named
            if (
              arg instanceof PyKeywordArgument || arg instanceof PyStarArgument ||
              n_param.isKeywordContainer() || n_param.isPositionalContainer()
            ) {
              seen_tuple_arg |= (arg == tuple_arg);
              seen_kwd_arg |= (arg == kwd_arg);
              unmatched_arg_iter.previous(); // step back
              break;
            }
            param_slots.put(n_param.getName(), arg); // it cannot yet contain this name unless function definition is broken
            ret.my_plain_mapped_params.put(arg, n_param);
          }
          else { // tuple: it may contain only positionals or other tuples.
            unmatched_arg_iter.previous(); // step back so that the visitor takes this arg again
            MyParamVisitor visitor = new MyParamVisitor(unmatched_arg_iter, ret);
            visitor.enterTuple(a_param.getAsTuple()); // will recurse as needed
            unmatched_subargs.addAll(visitor.getUnmatchedSubargs()); // what it's seen 
          }
          unmatched_arg_iter.remove(); // it has been matched
          param_index += 1;
        }
        if (!seen_kwd_arg) { // **kwd arg is the last; if it's present, checking the rest would be useless
          if (!seen_tuple_arg) { // any pos args can only come before *arg
            // some pos args might go to a *param
            if (tuple_slot != null) {
              while (unmatched_arg_iter.hasNext()) {
                PyExpression arg = unmatched_arg_iter.next();
                if (arg instanceof PyKeywordArgument) {
                  unmatched_arg_iter.previous(); // step back
                  break;
                }
                ret.my_plain_mapped_params.put(arg, tuple_slot);
                unmatched_arg_iter.remove(); // consumed as nameless
              }
            }
          }
          // check named args
          boolean seen_kwd = false;
          while (unmatched_arg_iter.hasNext()) {
            PyExpression arg = unmatched_arg_iter.next();
            if (arg instanceof PyKeywordArgument) {
              if (!seen_kwd_arg && !seen_tuple_arg) {
                final String argname = ((PyKeywordArgument)arg).getKeyword();
                if (param_slots.containsKey(argname)) { // slot is known
                  if (param_slots.get(argname) == null) { // slot is not filled
                    param_slots.put(argname, arg);
                    // we'll put() it to ret.my_plain_mapped_params later
                    seen_kwd = true;
                  }
                  else {
                    //getHolder().createErrorAnnotation(arg, "duplicate arg '" + argname + "'");
                    ret.markArgument(arg, ArgFlag.IS_DUP);
                  }
                  unmatched_arg_iter.remove(); // it has been matched or flagged, forget
                }
                // else: ignore unknown arg, we'll deal with them later
              }
              else {
                ret.markArgument(arg, ArgFlag.IS_UNMAPPED);
                //getHolder().createErrorAnnotation(arg, "cannot appear past an *arg");
                unmatched_arg_iter.remove(); // it has been flagged, forget
              }
            }
            else if (seen_kwd && (arg != kwd_arg)) {
              ret.markArgument(arg, ArgFlag.IS_POS_PAST_KWD);
              //getHolder().createErrorAnnotation(arg, "non-keyword arg after keyword arg");
              unmatched_arg_iter.remove(); // it has been flagged, forget
            }
            seen_tuple_arg |= (arg == tuple_arg);
            seen_kwd_arg |= (arg == kwd_arg);
          }
          // some named args might go to a **kwd param
          if (kwd_slot != null) {
            unmatched_arg_iter = unmatched_args.listIterator(); // anew
            while (unmatched_arg_iter.hasNext()) {
              PyExpression arg = unmatched_arg_iter.next();
              if (arg instanceof PyKeywordArgument) {
                ret.my_plain_mapped_params.put(arg, kwd_slot);
                unmatched_arg_iter.remove(); // consumed by **kwd
              }
              // no else: name errors are all detected above
            }
          }
        }
        if (seen_tuple_arg) { // link remaining params to *arg if present
          for (PyParameter a_param : params) {
            PyNamedParameter n_param = a_param.getAsNamed();
            if (n_param != null) {
              final String param_name = n_param.getName();
              if (
                  (n_param.getDefaultValue() == null) &&   // has no default value
                  param_slots.containsKey(param_name) && // known as a slot
                  (param_slots.get(param_name) == null)  // the slot yet unfilled
              ) {
                param_slots.put(param_name, tuple_arg);
                unmatched_args.remove(tuple_arg);
              }
            }
          }
        }
        if (seen_kwd_arg) { // link remaining params to **kwarg if present
          for (PyParameter a_param : params) {
            PyNamedParameter n_param = a_param.getAsNamed();
            if (n_param != null) {
              final String param_name = n_param.getName();
              if (
                  (n_param.getDefaultValue() == null) &&   // has no default value
                  param_slots.containsKey(param_name) && // known as a slot
                  (param_slots.get(param_name) == null)  // the slot yet unfilled
              ) {
                param_slots.put(param_name, kwd_arg);
                unmatched_args.remove(kwd_arg);
              }
            }
          }
        }
        // check and collect all yet unfilled params without default values
        Map<String, PyNamedParameter> unfilled_params = new HashMap<String, PyNamedParameter>();
        for (PyParameter a_param : params) {
          PyNamedParameter n_param = a_param.getAsNamed();
          if (n_param != null) {
            final String param_name = n_param.getName();
            if (
                (n_param.getDefaultValue() == null) &&   // has no default value
                param_slots.containsKey(param_name) && // known as a slot
                (param_slots.get(param_name) == null)  // the slot yet unfilled
            ) {
              if (tuple_arg != null) {
                // An *arg, if present, fills all positional params
                param_slots.put(param_name, tuple_arg);
                unmatched_args.remove(tuple_arg);
              }
              else {
                unfilled_params.put(param_name, n_param);
              }
            }
          }
        }
        // *arg and **kwarg are not in slots list; write any *param or **param off to them if present.
        if (kwd_arg != null && kwd_slot != null) {
          ret.my_kwd_mapped_params.add(kwd_slot);
          unmatched_args.remove(kwd_arg);
        }
        if (tuple_arg != null && tuple_slot != null) {
          ret.my_tuple_mapped_params.add(tuple_slot);
          unmatched_args.remove(tuple_arg);
        }
        // maybe we did not map a star arg because all eligible params have defaults; time to be less picky now. 
        if (tuple_arg != null && ret.my_tuple_mapped_params.isEmpty()) { // link remaining params to *arg if nothing else is mapped to it
          for (PyParameter a_param : params) {
            PyNamedParameter n_param = a_param.getAsNamed();
            if (n_param != null) {
              final String param_name = n_param.getName();
              if (
                  param_slots.containsKey(param_name) && // known as a slot
                  (param_slots.get(param_name) == null)  // the slot yet unfilled
              ) {
                param_slots.put(param_name, tuple_arg);
                unmatched_args.remove(tuple_arg);
              }
            }
          }
        }
        if (kwd_arg != null && ret.my_kwd_mapped_params.isEmpty()) { // link remaining params to **kwarg if nothing else is mapped to it
          for (PyParameter a_param : params) {
            PyNamedParameter n_param = a_param.getAsNamed();
            if (n_param != null) {
              final String param_name = n_param.getName();
              if (
                  param_slots.containsKey(param_name) && // known as a slot
                  (param_slots.get(param_name) == null)  // the slot yet unfilled
              ) {
                param_slots.put(param_name, kwd_arg);
                unmatched_args.remove(kwd_arg);
              }
            }
          }
        }
        // any args left?
        boolean tuple_arg_consumed_some = false;
        for (PyExpression arg : param_slots.values()) { // any(\x: x == tuple_arg)
          if (arg != null && arg == tuple_arg) {
            tuple_arg_consumed_some = true;
            break;
          }
        }
        for (PyExpression arg : unmatched_args) {
          //getHolder().createErrorAnnotation(arg, "unexpected arg");
          if (arg == kwd_arg && tuple_arg_consumed_some) continue; // *arg consumed anything that **arg might equally consume.
          ret.markArgument(arg, ArgFlag.IS_UNMAPPED);
        }
        // any params still unfilled?
        for (final PyNamedParameter param : unfilled_params.values()) {
          // getHolder().createErrorAnnotation(close_paren, "parameter '" + param_name + "' unfilled");
          ret.my_unmapped_params.add(param);
        }
        // copy the mapping of args
        for (PyParameter a_param : params) {
          PyNamedParameter n_param = a_param.getAsNamed();
          if (n_param != null) {
            PyExpression arg = param_slots.get(n_param.getName());
            if (arg != null) {
              if (arg instanceof PyStarArgument) {
                PyStarArgument star_arg = (PyStarArgument)arg;
                if (star_arg.isKeyword()) ret.my_kwd_mapped_params.add(n_param);
                else ret.my_tuple_mapped_params.add(n_param);
              }
              else ret.my_plain_mapped_params.put(arg, n_param);
            }
          }
        }
        // copy starred args
        ret.my_kwd_arg = kwd_arg;
        ret.my_tuple_arg = tuple_arg;
        // add unmatched nested arguments
        for (PyExpression subarg : unmatched_subargs) {
          ret.my_arg_flags.put(subarg, EnumSet.of(ArgFlag.IS_UNMAPPED));
        }
      }
    }
    return ret;
  }

  private static class MyParamVisitor extends PyElementVisitor {
    private Iterator<PyExpression> myArgIterator;
    private AnalysisResultImpl myResult;
    private List<PyExpression> myUnmatchedSubargs;

    private MyParamVisitor(Iterator<PyExpression> arg_iterator, AnalysisResultImpl ret) {
      myArgIterator = arg_iterator;
      myResult = ret;
      myUnmatchedSubargs = new ArrayList<PyExpression>(5); // arbitrary 'enough'
    }

    private Collection<PyExpression> getUnmatchedSubargs() {
      return myUnmatchedSubargs;
    }

    @Override
    public void visitPyParameter(PyParameter node) {
      PyNamedParameter named = node.getAsNamed();
      if (named != null) enterNamed(named);
      else enterTuple(node.getAsTuple());
    }

    public void enterTuple(PyTupleParameter param) {
      PyExpression arg = null;
      if (myArgIterator.hasNext()) arg = myArgIterator.next();
      // try to unpack a tuple expr in argument, if there's any
      PyExpression[] elements = null;
      if (arg instanceof PyParenthesizedExpression) {
        PyExpression inner_expr = ((PyParenthesizedExpression)arg).getContainedExpression();
        if (inner_expr instanceof PyTupleExpression) elements = ((PyTupleExpression)inner_expr).getElements();
      }
      else if (arg instanceof PyListLiteralExpression) {
        elements = ((PyListLiteralExpression)arg).getElements();
      }
      final PyParameter[] nested_params = param.getContents();
      if (elements != null) { // recursively map expression's tuple to parameter's.
        MyParamVisitor visitor = new MyParamVisitor(Arrays.asList(elements).iterator(), myResult);
        for (PyParameter nested : nested_params) nested.accept(visitor);
        myUnmatchedSubargs.addAll(visitor.getUnmatchedSubargs());
      }
      else { // map all what's inside to this arg
        final List<PyNamedParameter> nested_mapped = new ArrayList<PyNamedParameter>(nested_params.length);
        ParamHelper.walkDownParamArray(
          nested_params,
          new ParamHelper.ParamVisitor() {
            @Override public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
              nested_mapped.add(param);
            }
          }
        );
        myResult.my_nested_mapped_params.put(arg, nested_mapped);
      }
    }

    public void enterNamed(PyNamedParameter param) {
      if (myArgIterator.hasNext()) {
        PyExpression subarg = myArgIterator.next();
        myResult.my_plain_mapped_params.put(subarg, param);
      }
      else {
        myResult.my_unmapped_params.add(param);
      }
      // ...and *arg or **arg just won't parse inside a tuple, no need to handle it here
    }
  }

  private class AnalysisResultImpl implements PyArgumentList.AnalysisResult {

    private Map<PyExpression, PyNamedParameter> my_plain_mapped_params; // one param per arg
    private Map<PyExpression, List<PyNamedParameter>> my_nested_mapped_params; // one arg sweeps a nested tuple of params
    private PyStarArgument my_tuple_arg; // the *arg
    private PyStarArgument my_kwd_arg;   // the **arg
    private List<PyNamedParameter> my_tuple_mapped_params; // params mapped to *arg
    private List<PyNamedParameter> my_kwd_mapped_params;   // params mapped to **arg
    private List<PyNamedParameter> my_unmapped_params;
    private Map<PyExpression, EnumSet<PyArgumentList.ArgFlag>> my_arg_flags; // flags of every arg
    private PyCallExpression.PyMarkedFunction my_marked_func;

    public AnalysisResultImpl() {
      // full of empty containers
      my_plain_mapped_params = new HashMap<PyExpression, PyNamedParameter>();
      my_nested_mapped_params = new HashMap<PyExpression, List<PyNamedParameter>>(); 
      my_tuple_mapped_params = new ArrayList<PyNamedParameter>();
      my_kwd_mapped_params = new ArrayList<PyNamedParameter>();
      my_unmapped_params = new ArrayList<PyNamedParameter>();
      my_arg_flags = new HashMap<PyExpression, EnumSet<ArgFlag>>();
      my_marked_func = null;
    }

    /**
     * @return A mapping argument->parameter for non-starred arguments (but includes starred parameters).
     */
    public @NotNull Map<PyExpression, PyNamedParameter> getPlainMappedParams() {
      return my_plain_mapped_params;
    }

    @NotNull
    public Map<PyExpression, List<PyNamedParameter>> getNestedMappedParams() {
      return my_nested_mapped_params;
    }

    /**
     * @return First *arg, or null.
     */
    public PyStarArgument getTupleArg(){
      return my_tuple_arg;
    }

    /**
     * @return A list of parameters mapped to an *arg.
     */
    public @NotNull List<PyNamedParameter> getTupleMappedParams(){
      return my_tuple_mapped_params;
    }

    /**
     * @return First **arg, or null.
     */
    public PyStarArgument getKwdArg(){
      return my_kwd_arg;
    }

    /**
     * @return A list of parameters mapped to an **arg.
     */
    public @NotNull List<PyNamedParameter> getKwdMappedParams(){
      return my_kwd_mapped_params;
    }

    /**
     * @return A list of parameters for which no arguments were found ('missing').
     */
    public @NotNull
    List<PyNamedParameter> getUnmappedParams(){
      return my_unmapped_params;
    }

    /**
     * @return result of a resolveCallee() against the function call to which the paramater list belongs.
     */
    @Nullable
    public PyCallExpression.PyMarkedFunction getMarkedFunction() {
      return my_marked_func;
    }

    /**
     * @return Lists all args with their flags.
     */
    public Map<PyExpression, EnumSet<ArgFlag>> getArgumentFlags(){
      return my_arg_flags;
    }

    public PyArgumentList getArgumentList() {
      return PyArgumentListImpl.this; // that is, 'outer'
    }

    protected PyExpression markArgument(PyExpression arg, ArgFlag... flags) {
      EnumSet<ArgFlag> argflags = my_arg_flags.get(arg);
      if (argflags == null) {
        argflags = EnumSet.noneOf(ArgFlag.class);
      }
      argflags.addAll(Arrays.asList(flags));
      my_arg_flags.put(arg, argflags);
      return arg;
    }
  }


}
