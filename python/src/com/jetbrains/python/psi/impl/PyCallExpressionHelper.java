package com.jetbrains.python.psi.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Functions common to different implementors of PyCallExpression, with different base classes.
 * User: dcheryasov
 * Date: Dec 23, 2008 10:31:38 AM
 */
public class PyCallExpressionHelper {
  private PyCallExpressionHelper() {
    // none
  }

  /**
   * Adds an argument to the end of argument list.
   * @param us the arg list
   * @param expression what to add
   */
  public static void addArgument(PyCallExpression us, PyExpression expression) {
    PyExpression[] arguments = us.getArgumentList().getArguments();
    final PyExpression last_arg = arguments.length == 0 ? null : arguments[arguments.length - 1];
    PyElementGenerator.getInstance(us.getProject()).insertItemIntoList(us, last_arg, expression);
  }

  /**
   * Tries to interpret a call as a call to built-in {@code classmethod} or {@code staticmethod}.
   *
   * @param redefining_call the possible call, generally a result of chasing a chain of assignments
   * @param us              any in-project PSI element, used to determine SDK and ultimately builtins module used to check the wrapping functions
   * @return a pair of wrapper name and wrapped function; for {@code staticmethod(foo)} it would be ("staticmethod", foo).
   */
  @Nullable
  public static Pair<String, PyFunction> interpretAsStaticmethodOrClassmethodWrappingCall(PyCallExpression redefining_call, PsiElement us) {
    PyExpression redefining_callee = redefining_call.getCallee();
    if (redefining_callee instanceof PyReferenceExpression) {
      final PyReferenceExpression refex = (PyReferenceExpression)redefining_callee;
      final String refname = refex.getReferencedName();
      if ((PyNames.CLASSMETHOD.equals(refname) || PyNames.STATICMETHOD.equals(refname))) {
        PsiElement redefining_func = refex.getReference().resolve();
        if (redefining_func != null) {
          PsiElement true_func = PyBuiltinCache.getInstance(us).getByName(refname);
          if (true_func instanceof PyClass) true_func = ((PyClass)true_func).findInitOrNew(true);
          if (true_func == redefining_func) {
            // yes, really a case of "foo = classmethod(foo)"
            PyArgumentList arglist = redefining_call.getArgumentList();
            if (arglist != null) { // really can't be any other way
              PyExpression[] args = arglist.getArguments();
              if (args.length == 1) {
                PyExpression possible_original_ref = args[0];
                if (possible_original_ref instanceof PyReferenceExpression) {
                  PsiElement original = ((PyReferenceExpression)possible_original_ref).getReference().resolve();
                  if (original instanceof PyFunction) {
                    // pinned down the original; replace our resolved callee with it and add flags.
                    return new Pair<String, PyFunction>(refname, (PyFunction)original);
                  }
                }
              }
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  public static PyClass resolveCalleeClass(PyCallExpression us) {
    PyExpression callee = us.getCallee();

    PsiElement resolved;
    QualifiedResolveResult resolveResult = null;
    if (callee instanceof PyReferenceExpression) {
      // dereference
      PyReferenceExpression ref = (PyReferenceExpression)callee;
      resolveResult = ref.followAssignmentsChain(TypeEvalContext.fast());
      resolved = resolveResult.getElement();
    }
    else {
      resolved = callee;
    }
    // analyze
    if (resolved instanceof PyClass) {
      return (PyClass)resolved;
    }
    else if (resolved instanceof PyFunction) {
        final PyFunction pyFunction = (PyFunction) resolved;
        return pyFunction.getContainingClass();
    }

    return null;
  }

  @Nullable
  public static PyCallExpression.PyMarkedCallee resolveCallee(PyCallExpression us, TypeEvalContext context) {
    PyFunction.Flag wrappedFlag = null;
    boolean isConstructorCall = false;

    PyExpression callee = us.getCallee();
    PsiElement resolved;
    QualifiedResolveResult resolveResult = null;
    if (callee instanceof PyReferenceExpression) {
      // dereference
      PyReferenceExpression ref = (PyReferenceExpression)callee;
      resolveResult = ref.followAssignmentsChain(context);
      resolved = resolveResult.getElement();
    }
    else {
      resolved = callee;
    }
    // analyze
    if (resolved instanceof PyClass) {
      resolved = ((PyClass)resolved).findInitOrNew(true); // class to constructor call
      isConstructorCall = true;
    }
    else if (resolved instanceof PyCallExpression) {
      // is it a case of "foo = classmethod(foo)"?
      PyCallExpression redefiningCall = (PyCallExpression)resolved;
      Pair<String, PyFunction> wrapperInfo = interpretAsStaticmethodOrClassmethodWrappingCall(redefiningCall, us);
      if (wrapperInfo != null) {
        resolved = wrapperInfo.getSecond();
        String wrapper_name = wrapperInfo.getFirst();
        if (PyNames.CLASSMETHOD.equals(wrapper_name)) {
          wrappedFlag = PyFunction.Flag.CLASSMETHOD;
        }
        else if (PyNames.STATICMETHOD.equals(wrapper_name)) wrappedFlag = PyFunction.Flag.STATICMETHOD;
      }
    }
    if (resolved instanceof Callable) {
      EnumSet<PyFunction.Flag> flags = EnumSet.noneOf(PyFunction.Flag.class);
      List<PyExpression> qualifiers = resolveResult != null ? resolveResult.getQualifiers() : Collections.<PyExpression>emptyList();
      boolean is_by_instance = isConstructorCall ||
                               isQualifiedByInstance((Callable)resolved, qualifiers, context)
                               || resolved instanceof PyBoundFunction;
      PyExpression lastQualifier = qualifiers != null && qualifiers.isEmpty() ? null : qualifiers.get(qualifiers.size()-1);
      boolean isByClass = lastQualifier == null ? false : isQualifiedByClass((Callable)resolved, lastQualifier, context);
      final Callable callable = (Callable)resolved;
      int implicitOffset = getImplicitArgumentCount(callable, wrappedFlag, flags, is_by_instance, isByClass);
      if (!isConstructorCall && PyNames.NEW.equals(callable.getName())) {
        implicitOffset = Math.min(implicitOffset - 1, 0); // case of Class.__new__
      }
      implicitOffset = implicitOffset < 0? 0: implicitOffset; // wrong source can trigger strange behaviour
      return new PyCallExpression.PyMarkedCallee(callable, flags, implicitOffset,
                                                 resolveResult != null ? resolveResult.isImplicit() : false);
    }
    return null;
  }

  /**
   * Calls the {@link #getImplicitArgumentCount(PyExpression, Callable, PyFunction.Flag, EnumSet<PyFunction.Flag>, boolean) full version}
   * with null flags and with isByInstance inferred directly from call site (won't work with reassigned bound methods).
   *
   * @param callReference       the call site, where arguments are given.
   * @param functionBeingCalled resolved method which is being called; plain functions are OK but make little sense.
   * @param typeContext         shared type evaluation context
   * @return a non-negative number of parameters that are implicit to this call.
   */
  public static int getImplicitArgumentCount(
    @NotNull final PyReferenceExpression callReference,
    @NotNull PyFunction functionBeingCalled,
    @Nullable TypeEvalContext typeContext
  ) {
    //return getImplicitArgumentCount(functionBeingCalled, null, null, qualifierIsAnInstance(callReference, TypeEvalContext.fast()));
    if (typeContext == null) typeContext = TypeEvalContext.fast();
    final PyDecorator decorator = PsiTreeUtil.getParentOfType(callReference, PyDecorator.class);
    if (decorator != null && PsiTreeUtil.isAncestor(decorator.getCallee(), callReference, false)) {
      return 1;
    }
    QualifiedResolveResult followed = callReference.followAssignmentsChain(typeContext);
    boolean isByInstance = isQualifiedByInstance(functionBeingCalled, followed.getQualifiers(), typeContext);
    boolean isByClass = isQualifiedByInstance(functionBeingCalled, followed.getQualifiers(), typeContext);
    return getImplicitArgumentCount(functionBeingCalled, null, null, isByInstance, isByClass);
  }

  /**
   * Finds how many arguments are implicit in a given call.
   *
   * @param callable      resolved method which is being called; non-methods immediately return 0.
   * @param wrappedFlag   value of {@link com.jetbrains.python.psi.PyFunction.Flag#WRAPPED} if known.
   * @param flags         set of flags to be <i>updated</i> by this call; wrappedFlag's value ends up here, too.
   * @param isByInstance  true if the call is known to be by instance (not by class).
   * @return a non-negative number of parameters that are implicit to this call. E.g. for a typical method call 1 is returned
   *         because one parameter ('self') is implicit.
   */
  private static int getImplicitArgumentCount(
    Callable callable,
    @Nullable PyFunction.Flag wrappedFlag,
    @Nullable EnumSet<PyFunction.Flag> flags,
    boolean isByInstance,
    boolean isByClass
  ) {
    int implicit_offset = 0;
    if (isByInstance) implicit_offset += 1;
    PyFunction method = callable.asMethod();
    if (method == null) return implicit_offset;
    //
    // wrapped flags?
    if (wrappedFlag != null) {
      if (flags != null) {
        flags.add(wrappedFlag);
        flags.add(PyFunction.Flag.WRAPPED);
      }
      if (wrappedFlag == PyFunction.Flag.STATICMETHOD && implicit_offset > 0) {
        implicit_offset -= 1;
      } // might have marked it as implicit 'self'
      if (wrappedFlag == PyFunction.Flag.CLASSMETHOD && !isByInstance) {
        implicit_offset += 1;
      } // Both Foo.method() and foo.method() have implicit the first arg
    }
    if (!isByInstance && PyNames.NEW.equals(method.getName())) implicit_offset += 1; // __new__ call
    if (!isByInstance && !isByClass && PyNames.INIT.equals(method.getName())) {
      implicit_offset++;
    }

    // decorators?
    final String deconame = PyUtil.getClassOrStaticMethodDecorator(method);
    if (PyNames.STATICMETHOD.equals(deconame)) {
      if (flags != null) {
        flags.add(PyFunction.Flag.STATICMETHOD);
      }
      if (isByInstance && implicit_offset > 0) implicit_offset -= 1; // might have marked it as implicit 'self'
    }
    else if (PyNames.CLASSMETHOD.equals(deconame)) {
      if (flags != null) {
        flags.add(PyFunction.Flag.CLASSMETHOD);
      }
      if (!isByInstance) implicit_offset += 1; // Both Foo.method() and foo.method() have implicit the first arg
    }
    return implicit_offset;
  }

  private static boolean isQualifiedByInstance(Callable resolved, List<PyExpression> qualifiers, TypeEvalContext context) {
    PyDocStringOwner owner = PsiTreeUtil.getStubOrPsiParentOfType(resolved, PyDocStringOwner.class);
    if (!(owner instanceof PyClass)) {
      return false;
    }
    // true = call by instance
    if (qualifiers.isEmpty()) {
      return true; // unqualified + method = implicit constructor call
    }
    for (PyExpression qualifier : qualifiers) {
      if (isQualifiedByInstance(resolved, qualifier, context)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isQualifiedByInstance(Callable resolved, PyExpression qualifier, TypeEvalContext context) {
    if (isQualifiedByClass(resolved, qualifier, context)) {
      return false;
    }
    PyType qtype = context.getType(qualifier);
    if (qtype != null) {
      // TODO: handle UnionType
      if (qtype instanceof PyModuleType) return false; // qualified by module, not instance.
    }
    return true; // NOTE. best guess: unknown qualifier is more probably an instance.
  }

  private static boolean isQualifiedByClass(Callable resolved, PyExpression qualifier, TypeEvalContext context) {
    PyType qtype = context.getType(qualifier);
    if (qtype instanceof PyClassType) {
      if (((PyClassType)qtype).isDefinition()) {
        PyClass resolvedParent = PsiTreeUtil.getStubOrPsiParentOfType(resolved, PyClass.class);
        if (resolvedParent != null) {
          final PyClass qualifierClass = ((PyClassType)qtype).getPyClass();
          if (qualifierClass != null && (qualifierClass.isSubclass(resolvedParent) || resolvedParent.isSubclass(qualifierClass))) {
            return true;
          }
        }
      }
    }
    return false;
  }

  static boolean isCalleeText(PyCallExpression pyCallExpression, String[] nameCandidates) {
    final PyExpression callee = pyCallExpression.getCallee();
    if (!(callee instanceof PyReferenceExpression)) {
      return false;
    }
    for (String name : nameCandidates) {
      if (name.equals(((PyReferenceExpression)callee).getReferencedName())) {
        return true;
      }
    }
    return false;
  }


  static class MyParamVisitor extends PyElementVisitor {
    private final Iterator<PyExpression> myArgIterator;
    private final AnalysisResultImpl myResult;
    private final List<PyExpression> myUnmatchedSubargs;

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
        final Iterator<PyExpression> subargs_iterator = Arrays.asList(elements).iterator();
        MyParamVisitor visitor = new MyParamVisitor(subargs_iterator, myResult);
        for (PyParameter nested : nested_params) nested.accept(visitor);
        myUnmatchedSubargs.addAll(visitor.getUnmatchedSubargs());
        while (subargs_iterator.hasNext()) {  // more args in a tuple than parameters
          PyExpression overflown_arg = subargs_iterator.next();
          myResult.markArgument(overflown_arg, PyArgumentList.ArgFlag.IS_UNMAPPED);
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

  static class AnalysisResultImpl implements PyArgumentList.AnalysisResult {

    private final Map<PyExpression, PyNamedParameter> myPlainMappedParams; // one param per arg
    private final Map<PyExpression, List<PyNamedParameter>> myNestedMappedParams; // one arg sweeps a nested tuple of params
    private PyStarArgument myTupleArg; // the *arg
    private PyStarArgument myKwdArg;   // the **arg
    private final List<PyNamedParameter> myTupleMappedParams; // params mapped to *arg
    private final List<PyNamedParameter> myKwdMappedParams;   // params mapped to **arg
    private final List<PyNamedParameter> myUnmappedParams;
    private final Map<PyExpression, EnumSet<PyArgumentList.ArgFlag>> myArgFlags; // flags of every arg
    private PyCallExpression.PyMarkedCallee myMarkedCallee;
    private PyArgumentList myArgumentList;

    public AnalysisResultImpl(PyArgumentList arglist) {
      // full of empty containers
      myPlainMappedParams = new HashMap<PyExpression, PyNamedParameter>();
      myNestedMappedParams = new HashMap<PyExpression, List<PyNamedParameter>>();
      myTupleMappedParams = new ArrayList<PyNamedParameter>();
      myKwdMappedParams = new ArrayList<PyNamedParameter>();
      myUnmappedParams = new ArrayList<PyNamedParameter>();
      myArgFlags = new HashMap<PyExpression, EnumSet<PyArgumentList.ArgFlag>>();
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
     * @param type_context optional shared type evaluator / cache.
     */
    void mapArguments(
      PyExpression[] arguments,
      PyCallExpression.PyMarkedCallee resolved_callee,
      @Nullable TypeEvalContext type_context
    ) {
      if (type_context == null) type_context = TypeEvalContext.fast();
      myMarkedCallee = resolved_callee;
      List<PyExpression> unmatched_args = new LinkedList<PyExpression>();
      Collections.addAll(unmatched_args, arguments);
      final List<PyExpression> unmatched_subargs = new LinkedList<PyExpression>(); // unmatched nested arguments will go here
      // detect starred args
      for (PyExpression arg : arguments) {
        if (arg instanceof PyStarArgument) {
          PyStarArgument star_arg = (PyStarArgument)arg;
          if (star_arg.isKeyword()) {
            if (myKwdArg == null) myKwdArg = star_arg;
            else {
              markArgument(arg, PyArgumentList.ArgFlag.IS_DUP_KWD);
              unmatched_args.remove(arg);
            }
          }
          else {
            if (myTupleArg == null) myTupleArg = star_arg;
            else {
              markArgument(arg, PyArgumentList.ArgFlag.IS_DUP_TUPLE);
              unmatched_args.remove(arg);
            }
          }
        }
      }
      // prepare parameter slots
      final PyParameter[] parameters = myMarkedCallee.getCallable().getParameterList().getParameters();
      Map<PyNamedParameter, PyExpression> slots = new HashMap<PyNamedParameter, PyExpression>();
      PyNamedParameter kwd_par = null;   // **param
      PyNamedParameter tuple_par = null; // *param
      Set<PyExpression> mapped_args = new HashSet<PyExpression>();
      final int implicit_offset = resolved_callee.getImplicitOffset();
      int positional_index = 0; // up to this index parameters are positional
      // check positional arguments, fill slots
      int i = 0;
      for (PyParameter par : parameters) {
        if (tuple_par == null && kwd_par == null && positional_index < implicit_offset) {
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
      for (i=0; i < implicit_offset && i < parameters.length; i+=1) {
        slots.remove(parameters[i].getAsNamed());
        positional_index += 1;
      }
      // now params to the left of positional_index are positional.
      // map positional args to positional params.
      // we assume that implicitly skipped parameters are never nested tuples. no idea when they could ever be.
      int cnt = implicit_offset;
      int positional_bound = arguments.length; // to the right of this pos args are verboten
      ListIterator<PyExpression> unmatched_arg_iter = unmatched_args.listIterator();
      while (unmatched_arg_iter.hasNext()) {
        PyExpression arg = unmatched_arg_iter.next();
        if (arg instanceof PyStarArgument || arg instanceof PyKeywordArgument) {
          positional_bound = cnt;
          break;
        }
        if (cnt < parameters.length && cnt < positional_index) {
          final PyParameter par = parameters[cnt];
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
                PyType arg_type = type_context.getType(arg);
                if (arg_type != null && arg_type.isBuiltin(type_context) && "list".equals(arg_type.getName())) {
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
        markArgument(arg, PyArgumentList.ArgFlag.IS_UNMAPPED);
      }
      // mark past-bound positional args
      i = positional_bound;
      while (i<arguments.length) {
        PyExpression arg = arguments[i];
        if (!(arg instanceof PyStarArgument) && !(arg instanceof PyKeywordArgument)) {
          markArgument(arg, PyArgumentList.ArgFlag.IS_POS_PAST_KWD);
        }
        i += 1;
      }
      boolean seen_named_args = false;
      // map named args to named params if possible
      Map<String, PyNamedParameter> parameter_by_name = new HashMap<String, PyNamedParameter>();
      for (PyParameter par : parameters) {
        PyNamedParameter n_par = par.getAsNamed();
        if (n_par != null) parameter_by_name.put(n_par.getName(), n_par);
      }
      for (PyExpression arg : arguments) {
        if (arg instanceof PyKeywordArgument) { // to explicitly named param?
          String arg_name = ((PyKeywordArgument)arg).getKeyword();
          PyNamedParameter respective_par = parameter_by_name.get(arg_name);
          if (respective_par != null && !respective_par.isKeywordContainer() && !respective_par.isPositionalContainer()) {
            if (slots.get(respective_par) != null) markArgument(arg, PyArgumentList.ArgFlag.IS_DUP);
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
      if (cnt < parameters.length && cnt < positional_index && myTupleArg != null) {
        // check length of myTupleArg
        PyType tuple_arg_type = null;
        if (type_context != null) {
          final PyExpression expression = PsiTreeUtil.getChildOfType(myTupleArg, PyExpression.class);
          if (expression != null) {
            tuple_arg_type = type_context.getType(expression);
          }
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
        while (cnt < parameters.length && cnt < positional_index && mapped_params_count < tuple_length) {
          PyParameter par = parameters[cnt];
          if (par instanceof PySingleStarParameter) break;
          PyNamedParameter n_par = par.getAsNamed();
          if (slots.containsKey(n_par)) {
            final PyExpression arg_here = slots.get(n_par);
            if (arg_here != null) {
              if (tuple_length_known) {
                final EnumSet<PyArgumentList.ArgFlag> flags = myArgFlags.get(arg_here);
                if (flags == null || flags.isEmpty()) {
                  markArgument(arg_here, PyArgumentList.ArgFlag.IS_DUP);
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
        markArgument(myTupleArg, PyArgumentList.ArgFlag.IS_TOO_LONG);
      }
      // map unmapped named params to **kwarg
      if (myKwdArg != null) {
        for (PyParameter par : parameters) {
          PyNamedParameter n_par = par.getAsNamed();
          if (n_par != null && !n_par.isKeywordContainer() && !n_par.isPositionalContainer() && slots.get(n_par) == null) {
            slots.put(n_par, myKwdArg);
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
          final EnumSet<PyArgumentList.ArgFlag> flags = myArgFlags.get(arg);
          if (flags == null || flags.isEmpty()) {
            markArgument(arg, PyArgumentList.ArgFlag.IS_UNMAPPED);
          }
        }

      }
    }

    private static boolean isPositionalArg(PyExpression arg) {
      return !(arg instanceof PyKeywordArgument) && !(arg instanceof PyStarArgument);
    }

    public boolean isImplicitlyResolved() {
      return myMarkedCallee == null ? false : myMarkedCallee.isImplicitlyResolved();
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
    public Map<PyExpression, EnumSet<PyArgumentList.ArgFlag>> getArgumentFlags(){
      return myArgFlags;
    }

    public PyArgumentList getArgumentList() {
      return myArgumentList; // that is, 'outer'
    }

    protected PyExpression markArgument(PyExpression arg, PyArgumentList.ArgFlag... flags) {
      EnumSet<PyArgumentList.ArgFlag> argflags = myArgFlags.get(arg);
      if (argflags == null) {
        argflags = EnumSet.noneOf(PyArgumentList.ArgFlag.class);
      }
      ContainerUtil.addAll(argflags, flags);
      myArgFlags.put(arg, argflags);
      return arg;
    }
  }
}
