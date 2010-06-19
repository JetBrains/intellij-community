package com.jetbrains.python.psi.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.types.PyClassType;
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
          PsiNamedElement true_func = PyBuiltinCache.getInstance(us).getByName(refname, PsiNamedElement.class);
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
      resolveResult = ref.followAssignmentsChain();
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
  public static PyCallExpression.PyMarkedCallee resolveCallee(PyCallExpression us) {
    PyFunction.Flag wrappedFlag = null;
    boolean isConstructorCall = false;

    PyExpression callee = us.getCallee();
    PsiElement resolved;
    QualifiedResolveResult resolveResult = null;
    if (callee instanceof PyReferenceExpression) {
      // dereference
      PyReferenceExpression ref = (PyReferenceExpression)callee;
      resolveResult = ref.followAssignmentsChain();
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
      boolean is_by_instance = isByInstance(callReference);
      if (lastQualifier != null) {
        PyType qualifier_type = lastQualifier.getType(TypeEvalContext.fast()); // NOTE: ...or slow()?
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
    return getImplicitArgumentCount(callReference, functionBeingCalled, null, null, isByInstance(callReference));
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
    if (!isByInstance && PyNames.NEW.equals(method.getName())) implicit_offset += 1; // constructor call
    // decorators?
    if (PyNames.INIT.equals(method.getName())) {
      String refName = callReference instanceof PyReferenceExpression
                       ? ((PyReferenceExpression)callReference).getReferencedName()
                       : null;
      if (!PyNames.INIT.equals(refName)) {   // PY-312
        implicit_offset += 1;
      }
    }
    // look for closest decorator
    PyDecoratorList decolist = method.getDecoratorList();
    if (decolist != null) {
      PyDecorator[] decos = decolist.getDecorators();
      // TODO: look for all decorators
      if (decos.length == 1) {
        PyDecorator deco = decos[0];
        String deconame = deco.getName();
        if (deco.isBuiltin()) {
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
          // else could be custom decorator processing
        }
      }
    }
    return implicit_offset;
  }

  protected static boolean isByInstance(final PyExpression callee) {
    if (callee instanceof PyReferenceExpression) {
      PyExpression qualifier = ((PyReferenceExpression)callee).getQualifier();
      if (qualifier != null) {
        PyType type = qualifier.getType(TypeEvalContext.fast());
        if ((type instanceof PyClassType) && (!((PyClassType)type).isDefinition())) {
          // we're calling an instance method of qualifier
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Maps arguments of a call assuming it's a call to a builtin. No resolution or assignment chasing is done inside;
   * caller should have done that.
   * Class to constructor mapping is made inside, though.
   * @param call an unqualified call expression; name is directly searched in builtins.
   * @return analysis with argument mapping.
   */
  @Nullable
  public static PyArgumentList.AnalysisResult analyzeBuiltinCall(@NotNull PyCallExpression call) {
    final AnalysisResultImpl ret = new AnalysisResultImpl(call.getArgumentList());
    PyExpression[] arguments = call.getArguments();
    final String name = call.getCallee().getName();
    if (name != null) {
      PyFile builtins = PyBuiltinCache.getInstance(call).getBuiltinsFile();
      if (builtins != null) {
        int arg_offset = 0;
        EnumSet<PyFunction.Flag> flags = EnumSet.noneOf(PyFunction.Flag.class);
        PyFunction func = builtins.findTopLevelFunction(name);
        if (func == null) {
          PyClass cls = builtins.findTopLevelClass(name);
          if (cls != null) {
            func = cls.findInitOrNew(true);
            arg_offset = 1;
          }
        }
        if (func != null) {
          if (PyNames.NEW.equals(func.getName())) flags.add(PyFunction.Flag.CLASSMETHOD);
          ret.mapArguments(arguments, new PyCallExpression.PyMarkedCallee(func, flags, arg_offset, false));
          return ret;
        }
      }
    }
    return null;
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

  static class AnalysisResultImpl implements PyArgumentList.AnalysisResult {

    private final Map<PyExpression, PyNamedParameter> my_plain_mapped_params; // one param per arg
    private final Map<PyExpression, List<PyNamedParameter>> my_nested_mapped_params; // one arg sweeps a nested tuple of params
    private PyStarArgument my_tuple_arg; // the *arg
    private PyStarArgument my_kwd_arg;   // the **arg
    private final List<PyNamedParameter> my_tuple_mapped_params; // params mapped to *arg
    private final List<PyNamedParameter> my_kwd_mapped_params;   // params mapped to **arg
    private final List<PyNamedParameter> my_unmapped_params;
    private final Map<PyExpression, EnumSet<PyArgumentList.ArgFlag>> my_arg_flags; // flags of every arg
    private PyCallExpression.PyMarkedCallee my_marked_callee;
    private PyArgumentList my_argument_list;

    public AnalysisResultImpl(PyArgumentList arglist) {
      // full of empty containers
      my_plain_mapped_params = new HashMap<PyExpression, PyNamedParameter>();
      my_nested_mapped_params = new HashMap<PyExpression, List<PyNamedParameter>>();
      my_tuple_mapped_params = new ArrayList<PyNamedParameter>();
      my_kwd_mapped_params = new ArrayList<PyNamedParameter>();
      my_unmapped_params = new ArrayList<PyNamedParameter>();
      my_arg_flags = new HashMap<PyExpression, EnumSet<PyArgumentList.ArgFlag>>();
      my_marked_callee = null;
      my_argument_list = arglist;
    }

    /**
     * Maps arguments of a call to parameters of a callee.
     * must contain already resolved callee with flags set appropriately
     * @param arguments what to map, get if from call site
     * @param resolved_callee
     */
    void mapArguments(PyExpression[] arguments, PyCallExpression.PyMarkedCallee resolved_callee) {
      my_marked_callee = resolved_callee;
      Callable callable = resolved_callee.getCallable();
      PyParameterList paramlist = callable.getParameterList();
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
              markArgument(arg, PyArgumentList.ArgFlag.IS_DUP_KWD);
              //getHolder().createErrorAnnotation(arg, "duplicate **arg");
              unmatched_args.remove(arg); // error. ignore later
            }
          }
          else {
            if (tuple_arg == null) {
              tuple_arg = star_arg;
            }
            else {
              markArgument(arg, PyArgumentList.ArgFlag.IS_DUP_TUPLE);
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
            my_plain_mapped_params.put(arg, n_param);
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
            visitor.enterTuple(a_param.getAsTuple()); // will recurse as needed
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
              my_plain_mapped_params.put(arg, tuple_slot);
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
                  markArgument(arg, PyArgumentList.ArgFlag.IS_DUP);
                }
                unmatched_arg_iter.remove(); // it has been matched or flagged, forget
              }
              // else: ignore unknown arg, we'll deal with them later
            }
            else {
              markArgument(arg, PyArgumentList.ArgFlag.IS_UNMAPPED);
              //getHolder().createErrorAnnotation(arg, "cannot appear past an *arg");
              unmatched_arg_iter.remove(); // it has been flagged, forget
            }
          }
          else if (seen_kwd && (arg != kwd_arg)) {
            markArgument(arg, PyArgumentList.ArgFlag.IS_POS_PAST_KWD);
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
              my_plain_mapped_params.put(arg, kwd_slot);
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
        my_kwd_mapped_params.add(kwd_slot);
        unmatched_args.remove(kwd_arg);
      }
      if (tuple_arg != null && tuple_slot != null) {
        my_tuple_mapped_params.add(tuple_slot);
        unmatched_args.remove(tuple_arg);
      }
      // maybe we did not map a star arg because all eligible params have defaults; time to be less picky now.
      if (tuple_arg != null && my_tuple_mapped_params.isEmpty()) { // link remaining params to *arg if nothing else is mapped to it
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
      if (kwd_arg != null && my_kwd_mapped_params.isEmpty()) { // link remaining params to **kwarg if nothing else is mapped to it
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
        my_unmapped_params.add(param);
      }
      // copy the mapping of args
      for (PyParameter a_param : params) {
        PyNamedParameter n_param = a_param.getAsNamed();
        if (n_param != null) {
          PyExpression arg = param_slots.get(n_param.getName());
          if (arg != null) {
            if (arg instanceof PyStarArgument) {
              PyStarArgument star_arg = (PyStarArgument)arg;
              if (star_arg.isKeyword()) my_kwd_mapped_params.add(n_param);
              else my_tuple_mapped_params.add(n_param);
            }
            else my_plain_mapped_params.put(arg, n_param);
          }
        }
      }
      // copy starred args
      my_kwd_arg = kwd_arg;
      my_tuple_arg = tuple_arg;
      // add unmatched nested arguments
      for (PyExpression subarg : unmatched_subargs) {
        my_arg_flags.put(subarg, EnumSet.of(PyArgumentList.ArgFlag.IS_UNMAPPED));
      }
    }

    public boolean isImplicitlyResolved() {
      return my_marked_callee == null ? false : my_marked_callee.isImplicitlyResolved();
    }

    /**
     * @return A mapping argument->parameter for non-starred arguments (but includes starred parameters).
     */
    public @NotNull
    Map<PyExpression, PyNamedParameter> getPlainMappedParams() {
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
    public PyCallExpression.PyMarkedCallee getMarkedCallee() {
      return my_marked_callee;
    }

    /**
     * @return Lists all args with their flags.
     */
    public Map<PyExpression, EnumSet<PyArgumentList.ArgFlag>> getArgumentFlags(){
      return my_arg_flags;
    }

    public PyArgumentList getArgumentList() {
      return my_argument_list; // that is, 'outer'
    }

    protected PyExpression markArgument(PyExpression arg, PyArgumentList.ArgFlag... flags) {
      EnumSet<PyArgumentList.ArgFlag> argflags = my_arg_flags.get(arg);
      if (argflags == null) {
        argflags = EnumSet.noneOf(PyArgumentList.ArgFlag.class);
      }
      argflags.addAll(Arrays.asList(flags));
      my_arg_flags.put(arg, argflags);
      return arg;
    }
  }
}
