/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.impl;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
* @author yole
*/
public class CallArgumentsMappingImpl implements CallArgumentsMapping {

  private final Map<PyExpression, PyNamedParameter> myPlainMappedParams; // one param per arg
  private final Map<PyExpression, List<PyNamedParameter>> myNestedMappedParams; // one arg sweeps a nested tuple of params
  private PyStarArgument myTupleArg; // the *arg
  private PyStarArgument myKwdArg;   // the **arg
  private final List<PyNamedParameter> myTupleMappedParams; // params mapped to *arg
  private final List<PyNamedParameter> myKwdMappedParams;   // params mapped to **arg
  private final List<PyNamedParameter> myUnmappedParams;
  private final Map<PyExpression, EnumSet<ArgFlag>> myArgFlags; // flags of every arg
  private PyCallExpression.PyMarkedCallee myMarkedCallee;
  private PyArgumentList myArgumentList;

  public CallArgumentsMappingImpl(PyArgumentList arglist) {
    // full of empty containers
    myPlainMappedParams = new LinkedHashMap<PyExpression, PyNamedParameter>();
    myNestedMappedParams = new LinkedHashMap<PyExpression, List<PyNamedParameter>>();
    myTupleMappedParams = new ArrayList<PyNamedParameter>();
    myKwdMappedParams = new ArrayList<PyNamedParameter>();
    myUnmappedParams = new ArrayList<PyNamedParameter>();
    myArgFlags = new HashMap<PyExpression, EnumSet<ArgFlag>>();
    myMarkedCallee = null;
    myArgumentList = arglist;
  }

  /**
   * Maps arguments of a call to parameters of a callee.
   * must contain already resolved callee with flags set appropriately.
   * <br/>
   * <i>NOTE:</i> <tt>*arg</tt> of unknown length is considered to be long just enough to fill appropriate
   * positional paramaters, but at least one item long.
   * @param arguments what to map, get if from call site
   * @param resolved_callee what to map parameters of
   * @param context optional shared type evaluator / cache.
   */
  public void mapArguments(PyCallExpression.PyMarkedCallee resolved_callee, @NotNull TypeEvalContext context) {
    PyExpression[] arguments = myArgumentList.getArguments();
    myMarkedCallee = resolved_callee;
    final List<PyExpression> unmatched_subargs = new LinkedList<PyExpression>(); // unmatched nested arguments will go here
    List<PyExpression> unmatched_args = verifyArguments();

    final List<PyParameter> parameters = PyUtil.getParameters(myMarkedCallee.getCallable(), context);
    // prepare parameter slots
    Map<PyNamedParameter, PyExpression> slots = new LinkedHashMap<PyNamedParameter, PyExpression>();
    PyNamedParameter kwd_par = null;   // **param
    PyNamedParameter tuple_par = null; // *param
    Set<PyExpression> mapped_args = new HashSet<PyExpression>();
    final int implicitOffset = resolved_callee.getImplicitOffset();
    int positional_index = 0; // up to this index parameters are positional
    // check positional arguments, fill slots
    int i = 0;
    for (PyParameter par : parameters) {
      if (tuple_par == null && kwd_par == null && positional_index < implicitOffset) {
        positional_index += 1;
        continue;
      }
      PyNamedParameter n_par = par.getAsNamed();
      if (n_par != null) {
        if (n_par.isPositionalContainer()) tuple_par = n_par;
        else if (n_par.isKeywordContainer()) kwd_par = n_par;
        else {
          slots.put(n_par, null); // regular parameter that may serve as positional/named
          if (tuple_par == null && kwd_par == null) {
            positional_index += 1; // only if we're not past *param / **param
          }
        }
      }
      else {
        PyTupleParameter t_par = par.getAsTuple();
        if (t_par != null) positional_index += 1; // tuple can only be positional
        // else lone star, skip
      }
      i += 1;
    }
    // rule out 'self' or other implicit params
    for (i=0; i < implicitOffset && i < parameters.size(); i+=1) {
      slots.remove(parameters.get(i).getAsNamed());
      positional_index += 1;
    }
    // now params to the left of positional_index are positional.
    // map positional args to positional params.
    // we assume that implicitly skipped parameters are never nested tuples. no idea when they could ever be.
    int cnt = implicitOffset;
    int positional_bound = arguments.length; // to the right of this pos args are verboten
    ListIterator<PyExpression> unmatched_arg_iter = unmatched_args.listIterator();
    while (unmatched_arg_iter.hasNext()) {
      PyExpression arg = unmatched_arg_iter.next();
      if (arg instanceof PyStarArgument || arg instanceof PyKeywordArgument) {
        positional_bound = cnt;
        break;
      }
      if (cnt < parameters.size() && cnt < positional_index) {
        final PyParameter par = parameters.get(cnt);
        PyNamedParameter n_par = par.getAsNamed();
        if (n_par != null) {
          cnt += 1;
          slots.put(n_par, PyUtil.peelArgument(arg));
          mapped_args.add(arg);
        }
        else {
          PyTupleParameter t_par = par.getAsTuple();
          if (t_par != null) {
            if (arg instanceof PyParenthesizedExpression) {
              mapped_args.add(arg); // tuple itself is always mapped; its insides can fail
            }
            else {
              PyType arg_type = context.getType(arg);
              if (arg_type != null && arg_type.isBuiltin(context) && "list".equals(arg_type.getName())) {
                mapped_args.add(arg); // we can't really analyze arbitrary lists statically yet
                // but ListLiteralExpressions are handled by visitor
              }
            }
            unmatched_arg_iter.previous();
            MyParamVisitor visitor = new MyParamVisitor(unmatched_arg_iter, this);
            visitor.enterTuple(t_par.getAsTuple()); // will recur as needed
            unmatched_subargs.addAll(visitor.getUnmatchedSubargs()); // what didn't match inside
            cnt += 1;
          }
          // else: goes to *param
        }
      }
      else break;
    }
    // anything left after mapping of nested-tuple params?
    for (Map.Entry<PyExpression, List<PyNamedParameter>> pair : myNestedMappedParams.entrySet()) {
      PyExpression arg = pair.getKey();
      List<PyNamedParameter> params = pair.getValue();
      mapped_args.add(arg);
      for (PyNamedParameter n_par : params) slots.remove(n_par);
    }
    for (PyExpression arg : unmatched_subargs) {
      markArgument(arg, ArgFlag.IS_UNMAPPED);
    }


    boolean seen_named_args = false;
    // map named args to named params if possible
    Map<String, PyNamedParameter> parameter_by_name = new LinkedHashMap<String, PyNamedParameter>();
    for (PyParameter par : parameters) {
      PyNamedParameter n_par = par.getAsNamed();
      if (n_par != null) parameter_by_name.put(n_par.getName(), n_par);
    }
    for (PyExpression arg : arguments) {
      if (arg instanceof PyKeywordArgument) { // to explicitly named param?
        String arg_name = ((PyKeywordArgument)arg).getKeyword();
        PyNamedParameter respective_par = parameter_by_name.get(arg_name);
        if (respective_par != null && !respective_par.isKeywordContainer() && !respective_par.isPositionalContainer()) {
          if (slots.get(respective_par) != null) markArgument(arg, ArgFlag.IS_DUP);
          else slots.put(respective_par, arg);
        }
        else { // to **param?
          if (kwd_par != null) {
            myPlainMappedParams.put(arg, kwd_par);
            mapped_args.add(arg);
          }
        }
        seen_named_args = true;
      }
    }
    // map *arg to positional params if possible
    boolean tuple_arg_not_exhausted = false;
    boolean tuple_dup_found = false;
    if (cnt < parameters.size() && cnt < positional_index && myTupleArg != null) {
      // check length of myTupleArg
      PyType tuple_arg_type = null;
      final PyExpression expression = PsiTreeUtil.getChildOfType(myTupleArg, PyExpression.class);
      if (expression != null) {
        tuple_arg_type = context.getType(expression);
      }
      int tuple_length;
      boolean tuple_length_known;
      if (tuple_arg_type instanceof PyTupleType) {
        tuple_length = ((PyTupleType)tuple_arg_type).getElementCount();
        tuple_length_known = true;
      }
      else {
        tuple_length = 2000000; // no practical function will have so many positional params
        tuple_length_known = false;
      }
      int mapped_params_count = 0;
      while (cnt < parameters.size() && cnt < positional_index && mapped_params_count < tuple_length) {
        PyParameter par = parameters.get(cnt);
        if (par instanceof PySingleStarParameter) break;
        PyNamedParameter n_par = par.getAsNamed();
        if (slots.containsKey(n_par)) {
          final PyExpression arg_here = slots.get(n_par);
          if (arg_here != null) {
            if (tuple_length_known) {
              final EnumSet<ArgFlag> flags = myArgFlags.get(arg_here);
              if (flags == null || flags.isEmpty()) {
                markArgument(arg_here, ArgFlag.IS_DUP);
                tuple_dup_found = true;
              }
            }
            // else: unknown tuple length is just enough
            // the spree is over
            break;
          }
          else if (n_par != null) { // normally always true
            myTupleMappedParams.add(n_par);
            mapped_args.add(myTupleArg);
            slots.remove(n_par);
          }
        }
        else if (n_par == tuple_par) {
          mapped_params_count = tuple_length; // we found *param for our *arg, consider it fully mapped
          break;
        }
        cnt += 1;
        mapped_params_count += 1;
      }
      if (
        tuple_length_known && (mapped_params_count < tuple_length) || // not exhausted
        mapped_params_count == 0 // unknown length must consume at least first param
        ) {
        tuple_arg_not_exhausted = true;
      }
    }
    // map *param to the leftmost chunk of unmapped positional args
    // NOTE: ignores the structure of nested-tuple params!
    if (tuple_par != null) {
      i = 0;
      while (i < arguments.length && mapped_args.contains(arguments[i]) && isPositionalArg(arguments[i])) {
        i += 1; // skip first mapped args
      }
      if (i < arguments.length && isPositionalArg(arguments[i])) {
        while (i < arguments.length && !mapped_args.contains(arguments[i]) && isPositionalArg(arguments[i])) {
          myPlainMappedParams.put(arguments[i], tuple_par);
          mapped_args.add(arguments[i]);
          i += 1;
        }
      }
    }
    // map unmapped *arg to *param
    if (myTupleArg != null && tuple_par != null) {
      if (!mapped_args.contains(myTupleArg)) {
        myTupleMappedParams.add(tuple_par);
        mapped_args.add(myTupleArg);
      }
      else if (! seen_named_args && tuple_arg_not_exhausted) {
        // case of (*(1, 2, 3)) -> (a, *b); map the rest of *arg to *param
        myTupleMappedParams.add(tuple_par);
        mapped_args.add(myTupleArg);
        tuple_arg_not_exhausted = false;
      }
    }
    if (tuple_arg_not_exhausted && ! tuple_dup_found) {
      markArgument(myTupleArg, ArgFlag.IS_TOO_LONG);
    }
    // map unmapped named params to **kwarg
    if (myKwdArg != null) {
      for (int j = implicitOffset; j != parameters.size(); ++j) {
        final PyParameter par = parameters.get(j);
        final PyNamedParameter namedParameter = par.getAsNamed();
        if (namedParameter != null && !namedParameter.isKeywordContainer()
            && !namedParameter.isPositionalContainer() && slots.get(namedParameter) == null) {
          slots.put(namedParameter, myKwdArg);
        }
      }
    }
    // map unmapped **kwarg to **param
    if (myKwdArg != null && kwd_par != null && !mapped_args.contains(myKwdArg)) {
      myKwdMappedParams.add(kwd_par);
      mapped_args.add(myKwdArg);
    }
    // fill in ret, mark unmapped named params
    for (Map.Entry<PyNamedParameter, PyExpression> pair : slots.entrySet()) {
      PyNamedParameter n_par = pair.getKey();
      PyExpression arg = pair.getValue();
      if (arg == null) {
        if (!n_par.hasDefaultValue()) myUnmappedParams.add(n_par);
      }
      else {
        if (arg == myTupleArg) {
          myTupleMappedParams.add(n_par);
        }
        else if (arg == myKwdArg) {
          myKwdMappedParams.add(n_par);
        }
        else {
          myPlainMappedParams.put(arg, n_par);
        }
      }
    }
    // mark unmapped args
    for (PyExpression arg : slots.values()) {
      if (arg != null) mapped_args.add(arg);
    }
    for (PyExpression arg : arguments) {
      if (!mapped_args.contains(arg)) {
        final EnumSet<ArgFlag> flags = myArgFlags.get(arg);
        if (flags == null || flags.isEmpty()) {
          markArgument(arg, ArgFlag.IS_UNMAPPED);
        }
      }
    }
  }

  public List<PyExpression> verifyArguments() {
    List<PyExpression> unmatched_args = new LinkedList<PyExpression>();
    Collections.addAll(unmatched_args, myArgumentList.getArguments());
    // detect starred args
    for (PyExpression arg : myArgumentList.getArguments()) {
      if (arg instanceof PyStarArgument) {
        PyStarArgument star_arg = (PyStarArgument)arg;
        if (star_arg.isKeyword()) {
          if (myKwdArg == null) myKwdArg = star_arg;
          else {
            markArgument(arg, ArgFlag.IS_DUP_KWD);
            unmatched_args.remove(arg);
          }
        }
        else {
          if (myTupleArg == null) myTupleArg = star_arg;
          else {
            markArgument(arg, ArgFlag.IS_DUP_TUPLE);
            unmatched_args.remove(arg);
          }
        }
      }
    }

    markPastBoundPositionalArguments(myArgumentList.getArguments());
    return unmatched_args;
  }

  private void markPastBoundPositionalArguments(PyExpression[] arguments) {
    boolean seenKwArg = false;
    boolean seenKeyword = false;
    boolean seenStar = false;
    for (PyExpression arg : arguments) {
      if (arg == myKwdArg) {
        seenKwArg = true;
      }
      else if (arg instanceof PyKeywordArgument) {
        seenKeyword = true;
      }
      else if (arg instanceof PyStarArgument) {
        seenStar = true;
      }

      if (seenKeyword || seenKwArg || seenStar) {
        if (!(arg instanceof PyStarArgument) && (seenKwArg || !(arg instanceof PyKeywordArgument))) {
          markArgument(arg, ArgFlag.IS_POS_PAST_KWD);
        }
      }
    }
  }

  private static boolean isPositionalArg(PyExpression arg) {
    return !(arg instanceof PyKeywordArgument) && !(arg instanceof PyStarArgument);
  }

  /**
   * @return A mapping argument->parameter for non-starred arguments (but includes starred parameters).
   */
  public @NotNull
  Map<PyExpression, PyNamedParameter> getPlainMappedParams() {
    return myPlainMappedParams;
  }

  @NotNull
  public Map<PyExpression, List<PyNamedParameter>> getNestedMappedParams() {
    return myNestedMappedParams;
  }

  /**
   * @return First *arg, or null.
   */
  public PyStarArgument getTupleArg(){
    return myTupleArg;
  }

  /**
   * @return A list of parameters mapped to an *arg.
   */
  public @NotNull List<PyNamedParameter> getTupleMappedParams(){
    return myTupleMappedParams;
  }

  /**
   * @return First **arg, or null.
   */
  public PyStarArgument getKwdArg(){
    return myKwdArg;
  }

  /**
   * @return A list of parameters mapped to an **arg.
   */
  public @NotNull List<PyNamedParameter> getKwdMappedParams(){
    return myKwdMappedParams;
  }

  /**
   * @return A list of parameters for which no arguments were found ('missing').
   */
  public @NotNull
  List<PyNamedParameter> getUnmappedParams(){
    return myUnmappedParams;
  }

  /**
   * @return result of a resolveCallee() against the function call to which the paramater list belongs.
   */
  @Nullable
  public PyCallExpression.PyMarkedCallee getMarkedCallee() {
    return myMarkedCallee;
  }

  /**
   * @return Lists all args with their flags.
   */
  public Map<PyExpression, EnumSet<ArgFlag>> getArgumentFlags(){
    return myArgFlags;
  }

  @Override
  public boolean hasProblems() {
    for (Map.Entry<PyExpression, EnumSet<CallArgumentsMapping.ArgFlag>> arg_entry : myArgFlags.entrySet()) {
      EnumSet<CallArgumentsMapping.ArgFlag> flags = arg_entry.getValue();
      if (!flags.isEmpty()) return true;
    }
    return myUnmappedParams.size() > 0;
  }

  public PyArgumentList getArgumentList() {
    return myArgumentList; // that is, 'outer'
  }

  protected PyExpression markArgument(PyExpression arg, ArgFlag... flags) {
    EnumSet<ArgFlag> argflags = myArgFlags.get(arg);
    if (argflags == null) {
      argflags = EnumSet.noneOf(ArgFlag.class);
    }
    ContainerUtil.addAll(argflags, flags);
    myArgFlags.put(arg, argflags);
    return arg;
  }

  static class MyParamVisitor extends PyElementVisitor {
    private final Iterator<PyExpression> myArgIterator;
    private final CallArgumentsMappingImpl myResult;
    private final List<PyExpression> myUnmatchedSubargs;

    private MyParamVisitor(Iterator<PyExpression> arg_iterator, CallArgumentsMappingImpl ret) {
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
        final Iterator<PyExpression> subargs_iterator = Arrays.asList(elements).iterator();
        MyParamVisitor visitor = new MyParamVisitor(subargs_iterator, myResult);
        for (PyParameter nested : nested_params) nested.accept(visitor);
        myUnmatchedSubargs.addAll(visitor.getUnmatchedSubargs());
        while (subargs_iterator.hasNext()) {  // more args in a tuple than parameters
          PyExpression overflown_arg = subargs_iterator.next();
          myResult.markArgument(overflown_arg, ArgFlag.IS_UNMAPPED);
        }
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
        myResult.myNestedMappedParams.put(arg, nested_mapped);
      }
    }

    public void enterNamed(PyNamedParameter param) {
      if (myArgIterator.hasNext()) {
        PyExpression subarg = myArgIterator.next();
        myResult.myPlainMappedParams.put(subarg, param);
      }
      else {
        myResult.myUnmappedParams.add(param);
      }
      // ...and *arg or **arg just won't parse inside a tuple, no need to handle it here
    }
  }
}
