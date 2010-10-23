package com.jetbrains.python.psi.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
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

  public static void addArgument(PyCallExpression us, PyExpression expression) {
    PyExpression[] arguments = us.getArgumentList().getArguments();
    PyElementGenerator.getInstance(us.getProject()).insertItemIntoList(us,
                                                                       arguments.length == 0 ? null : arguments[arguments.length - 1],
                                                                       expression);
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
      PyExpression lastQualifier = resolveResult != null ? resolveResult.getLastQualifier() : null;
      final PyExpression callReference = us.getCallee();
      boolean is_by_instance = isConstructorCall || /*isByInstance(callReference, context);*/
              divinate(us.getCallee(), (Callable)resolved, lastQualifier, context);
      if (lastQualifier != null) {
        PyType qualifier_type = context.getType(lastQualifier);
        is_by_instance |=
          (qualifier_type != null && qualifier_type instanceof PyClassType && !((PyClassType)qualifier_type).isDefinition());
      }
      final Callable callable = (Callable)resolved;
      int implicitOffset = getImplicitArgumentCount(callReference, callable, wrappedFlag, flags, is_by_instance);
      if (!isConstructorCall && PyNames.NEW.equals(callable.getName())) {
        implicitOffset = Math.min(implicitOffset - 1, 0); // case of Class.__new__
      }
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
   * @return a non-negative number of parameters that are implicit to this call.
   */
  public static int getImplicitArgumentCount(final PyExpression callReference, PyFunction functionBeingCalled) {
    return getImplicitArgumentCount(callReference, functionBeingCalled, null, null, isByInstance(callReference, TypeEvalContext.fast()));
  }

  /**
   * Finds how many arguments are implicit in a given call.
   *
   * @param callReference the call site, where arguments are given.
   * @param callable      resolved method which is being called; other callables are OK but immediately return 0.
   * @param wrappedFlag   value of {@link PyFunction.Flag#WRAPPED} if known.
   * @param flags         set of flags to be <i>updated</i> by this call; wrappedFlag's value ends up here, too.
   * @param isByInstance  true if the call is known to be by instance (not by class).
   * @return a non-negative number of parameters that are implicit to this call. E.g. for a typical method call 1 is returned
   *         because one parameter ('self') is implicit.
   */
  private static int getImplicitArgumentCount(final PyExpression callReference,
                                              Callable callable,
                                              @Nullable PyFunction.Flag wrappedFlag,
                                              @Nullable EnumSet<PyFunction.Flag> flags,
                                              boolean isByInstance
  ) {
    int implicit_offset = 0;
    PyFunction method = callable.asMethod();
    if (method == null) return implicit_offset;
    //
    if (isByInstance) implicit_offset += 1;
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
    /*
    if (PyNames.INIT.equals(method.getName())) {
      String refName = callReference instanceof PyReferenceExpression
                       ? ((PyReferenceExpression)callReference).getReferencedName()
                       : null;
      if (!PyNames.INIT.equals(refName)) {   // PY-312
        implicit_offset += 1;
      }
    }
    */
    // decorators?
    // look for closest decorator
    PyDecoratorList decolist = method.getDecoratorList();
    if (decolist != null) {
      PyDecorator[] decos = decolist.getDecorators();
      // TODO: look for all decorators
      if (decos.length == 1) {
        PyDecorator deco = decos[0];
        String deconame = deco.getName();
        // rare case, remove check for better performance: if (deco.isBuiltin()) {
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
        //}
      }
    }
    return implicit_offset;
  }

  protected static boolean isByInstance(final PyExpression callee, TypeEvalContext context) {
    if (callee instanceof PyReferenceExpression) {
      PyExpression qualifier = ((PyReferenceExpression)callee).getQualifier();
      if (qualifier != null) {
        PyType type = context.getType(qualifier);
        if ((type instanceof PyClassType) && (!((PyClassType)type).isDefinition())) {
          // we're calling an instance method of qualifier
          return true;
        }
      }
    }
    return false;
  }

  private static boolean divinate(PyExpression callee, Callable resolved, PyExpression lastQualifier, TypeEvalContext context) {
    // true = call by instance
    PyFunction method = resolved.asMethod();
    if (method != null) {
      if (lastQualifier == null) return true; // unqualified + method = implicit constructor call
      PyType qtype = context.getType(lastQualifier);
      if (qtype != null) {
        if (qtype instanceof PyClassType) {
          return ! ((PyClassType)qtype).isDefinition();
        }
        else return true; // TODO: handle UnionType
      }
      else return true; // NOTE. best guess: unknown qualifier is more probably an instance.
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
     * must contain already resolved callee with flags set appropriately
     * @param arguments what to map, get if from call site
     * @param resolved_callee
     */
    void mapArguments(PyExpression[] arguments, PyCallExpression.PyMarkedCallee resolved_callee, LanguageLevel language_level) {
      myMarkedCallee = resolved_callee;
      Callable callable = resolved_callee.getCallable();
      PyParameterList paramlist = callable.getParameterList();
      PyParameter[] params = paramlist.getParameters();
      // prepare args and slots
      List<PyExpression> unmatched_args = new LinkedList<PyExpression>();
      Collections.addAll(unmatched_args, arguments);
      Map<String, PyExpression> param_slots = new HashMap<String, PyExpression>();
      PyNamedParameter kwd_slot = null; // the **kwd
      PyNamedParameter tuple_slot = null; // the *tuple. might be just boolean, but this way debugging is easier
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
      // look for star args, mark duplicate star args
      for (PyExpression arg : arguments) {
        if (arg instanceof PyStarArgument) {
          final PyStarArgument star_arg = (PyStarArgument)arg;
          if (star_arg.isKeyword()) {
            if (kwd_arg == null) kwd_arg = star_arg;
            else {
              markArgument(arg, PyArgumentList.ArgFlag.IS_DUP_KWD);
              unmatched_args.remove(arg); // error. ignore later
            }
          }
          else {
            if (tuple_arg == null) {
              tuple_arg = star_arg;
            }
            else {
              markArgument(arg, PyArgumentList.ArgFlag.IS_DUP_TUPLE);
              unmatched_args.remove(arg); // error. ignore later
            }
          }
        }
      }
      // rule out 'self' or other implicit params
      int param_index = 0;
      for (int i=0; i < resolved_callee.getImplicitOffset() && i < params.length; i+=1) {
        param_slots.remove(params[i].getName());
        param_index += 1;
      }
      boolean seen_tuple_arg = false;
      boolean seen_kwd_arg = false;
      boolean seenSingleStar = false;
      ListIterator<PyExpression> unmatched_arg_iter = unmatched_args.listIterator();
      // check positional args
      while (unmatched_arg_iter.hasNext() && (param_index < params.length)) {
        PyParameter a_param = params[param_index];      // its matching param
        if (a_param instanceof PySingleStarParameter) {
          param_index++;
          seenSingleStar = true;
          continue;
        }
        final PyExpression arg = unmatched_arg_iter.next(); // current arg
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
          if (!seenSingleStar) {
            param_slots.put(n_param.getName(), arg); // it cannot yet contain this name unless function definition is broken
            myPlainMappedParams.put(arg, n_param);
          }
          else {
            param_index++;
            continue;
          }
        }
        else { // tuple: it may contain only positionals or other tuples.
          PyTupleParameter tupleParameter = a_param.getAsTuple();
          if (tupleParameter != null) {
            unmatched_arg_iter.previous(); // step back so that the visitor takes this arg again
            MyParamVisitor visitor = new MyParamVisitor(unmatched_arg_iter, this);
            visitor.enterTuple(a_param.getAsTuple()); // will recur as needed
            unmatched_subargs.addAll(visitor.getUnmatchedSubargs()); // what it's seen
          }
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
              myPlainMappedParams.put(arg, tuple_slot);
              unmatched_arg_iter.remove(); // consumed as nameless
            }
          }
        }
        else if (tuple_slot != null) {
          // if we have both *param and *arg, any parameters up to *param are mapped to *arg;
          // syntax requires that any args before *arg can only be positional, for named params or not.
          for (int i=0; params[i] != tuple_slot; i+=1) {
            if (params[i] instanceof PyNamedParameter) {
              PyNamedParameter param = (PyNamedParameter)params[i];
              myPlainMappedParams.put(tuple_arg, param);
              param_slots.put(param.getName(), tuple_arg);
            }
            // else: a non-named param is either *param or **param; further checks will handle these
          }
        }
        // check named args
        boolean seen_kwd = false;
        while (unmatched_arg_iter.hasNext()) {
          PyExpression arg = unmatched_arg_iter.next();
          if (arg instanceof PyKeywordArgument) {
            if (!seen_kwd_arg && (!seen_tuple_arg || language_level.isAtLeast(LanguageLevel.PYTHON30))) {
              final String argname = ((PyKeywordArgument)arg).getKeyword();
              if (param_slots.containsKey(argname)) { // slot is known
                if (param_slots.get(argname) == null) { // slot is not filled
                  param_slots.put(argname, arg);
                  // we'll put() it to ret.myPlainMappedParams later
                  seen_kwd = true;
                }
                else markArgument(arg, PyArgumentList.ArgFlag.IS_DUP);
                unmatched_arg_iter.remove(); // it has been matched or flagged, forget
              }
              // else: ignore unknown arg, we'll deal with them later
            }
            else {
              markArgument(arg, PyArgumentList.ArgFlag.IS_UNMAPPED);
              unmatched_arg_iter.remove(); // it has been flagged, forget
            }
          }
          else if (seen_kwd && (arg != kwd_arg) && (arg != tuple_arg)) {
            markArgument(arg, PyArgumentList.ArgFlag.IS_POS_PAST_KWD);
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
              myPlainMappedParams.put(arg, kwd_slot);
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
                (!n_param.hasDefaultValue()) &&   // has no default value
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
                (!n_param.hasDefaultValue()) &&   // has no default value
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
              (!n_param.hasDefaultValue()) &&   // has no default value
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
        myKwdMappedParams.add(kwd_slot);
        unmatched_args.remove(kwd_arg);
      }
      if (tuple_arg != null && tuple_slot != null) {
        myTupleMappedParams.add(tuple_slot);
        unmatched_args.remove(tuple_arg);
      }
      // maybe we did not map a star arg because all eligible params have defaults; time to be less picky now.
      if (tuple_arg != null && myTupleMappedParams.isEmpty()) { // link remaining params to *arg if nothing else is mapped to it
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
      if (kwd_arg != null && myKwdMappedParams.isEmpty()) { // link remaining params to **kwarg if nothing else is mapped to it
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
        markArgument(arg, PyArgumentList.ArgFlag.IS_UNMAPPED);
      }
      // any params still unfilled?
      for (final PyNamedParameter param : unfilled_params.values()) {
        // getHolder().createErrorAnnotation(close_paren, "parameter '" + param_name + "' unfilled");
        myUnmappedParams.add(param);
      }
      // copy the mapping of args
      for (PyParameter a_param : params) {
        PyNamedParameter n_param = a_param.getAsNamed();
        if (n_param != null) {
          PyExpression arg = param_slots.get(n_param.getName());
          if (arg != null) {
            if (arg instanceof PyStarArgument) {
              PyStarArgument star_arg = (PyStarArgument)arg;
              if (star_arg.isKeyword()) myKwdMappedParams.add(n_param);
              else myTupleMappedParams.add(n_param);
            }
            else myPlainMappedParams.put(arg, n_param);
          }
        }
      }
      // copy starred args
      myKwdArg = kwd_arg;
      myTupleArg = tuple_arg;
      // add unmatched nested arguments
      for (PyExpression subarg : unmatched_subargs) {
        myArgFlags.put(subarg, EnumSet.of(PyArgumentList.ArgFlag.IS_UNMAPPED));
      }
    }


    void mapArguments2(PyExpression[] arguments, PyCallExpression.PyMarkedCallee resolved_callee, LanguageLevel language_level) {
      TypeEvalContext type_context = TypeEvalContext.fast(); // TODO: get it from parameters
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
            slots.put(n_par, null); // regular parameter that may serve as positional
            positional_index += 1;
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
            slots.put(n_par, arg);
            mapped_args.add(arg);
          }
          else {
            PyTupleParameter t_par = par.getAsTuple();
            if (t_par != null) {
              if (arg instanceof PyParenthesizedExpression) {
                mapped_args.add(arg); // tuple itself is always mapped; its insides can fail
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
      // anything left after mapping of tuple params?
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
        }
      }
      // map *arg to positional params if possible
      if (cnt < parameters.length && cnt < positional_index && myTupleArg != null) {
        // check length of myTupleArg
        PyType tuple_arg_type = null;
        if (type_context != null) {
          tuple_arg_type = type_context.getType(PsiTreeUtil.getChildOfType(myTupleArg, PyExpression.class));
        }
        int tuple_length = -1;
        boolean tuple_length_known = false;
        if (tuple_arg_type instanceof PyTupleType) {
          tuple_length = ((PyTupleType)tuple_arg_type).getElementCount();
          tuple_length_known = true;
        }
        i = 1;
        while (cnt < parameters.length && cnt < positional_index) {
          PyParameter par = parameters[cnt];
          if (par instanceof PySingleStarParameter) break;
          PyNamedParameter n_par = par.getAsNamed();
          if (slots.containsKey(n_par)) {
            final PyExpression arg_here = slots.get(n_par);
            final boolean over_tuple_length = tuple_length_known && i > tuple_length;
            if (over_tuple_length || arg_here != null) {
              /*
              if (!over_tuple_length && arg_here != null) {
                // tuple would overwrite these
                markArgument(arg_here, PyArgumentList.ArgFlag.IS_DUP);
                myTupleMappedParams.add(n_par);
                mapped_args.add(myTupleArg);
              }
              */
              // the spree is over
              break;
            }
            else if (n_par != null) { // normally always true
              myTupleMappedParams.add(n_par);
              mapped_args.add(myTupleArg);
              slots.remove(n_par);
            }
          }
          cnt += 1;
          i += 1;
        }
        if (tuple_length_known && i <= tuple_length) {
          markArgument(myTupleArg, PyArgumentList.ArgFlag.IS_TOO_LONG);
        }
      }
      // map *param to the leftmost chunk of unmapped positional args
      // NOTE: will fail on nested-tuple params!
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
      if (myTupleArg != null && !mapped_args.contains(myTupleArg) && tuple_par != null) {
        myTupleMappedParams.add(tuple_par);
        mapped_args.add(myTupleArg);
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
