package com.jetbrains.python.refactoring.classes.membersManager;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.jetbrains.NotNullPredicate;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Plugin that moves class methods
 *
 * @author Ilya.Kazakevich
 */
class MethodsManager extends MembersManager<PyFunction> {
  private static final String ABC_META_CLASS = "ABCMeta";

  /**
   * Some decorators should be copied with methods if method is marked abstract. Here is list.
   */
  private static final String[] DECORATORS_MAY_BE_COPIED_TO_ABSTRACT =
    {PyNames.PROPERTY, PyNames.CLASSMETHOD, PyNames.STATICMETHOD};

  public static final String ABC_META_PACKAGE = "abc";

  MethodsManager() {
    super(PyFunction.class);
  }

  @NotNull
  @Override
  protected List<PyElement> getMembersCouldBeMoved(@NotNull final PyClass pyClass) {
    return Lists.<PyElement>newArrayList(filterNameless(Arrays.asList(pyClass.getMethods())));
  }

  @Override
  protected Collection<PyElement> moveMembers(@NotNull final PyClass from,
                                              @NotNull final Collection<PyMemberInfo<PyFunction>> members,
                                              @NotNull final PyClass... to) {
    final Collection<PyFunction> methodsToMove = fetchElements(Collections2.filter(members, new AbstractFilter(false)));
    final Collection<PyFunction> methodsToAbstract = fetchElements(Collections2.filter(members, new AbstractFilter(true)));

    makeMethodsAbstract(methodsToAbstract, to);
    return moveMethods(from, methodsToMove, to);
  }

  /**
   * Creates abstract version of each method in each class (does not touch method itself as opposite to {@link #moveMethods(com.jetbrains.python.psi.PyClass, java.util.Collection, com.jetbrains.python.psi.PyClass...)})
   *
   * @param currentFunctions functions to make them abstract
   * @param to               classes where abstract method should be created
   */
  private static void makeMethodsAbstract(final Collection<PyFunction> currentFunctions, final PyClass... to) {
    final Set<PsiFile> filesToCheckImport = new HashSet<PsiFile>();
    final Set<PyClass> classesToAddMetaAbc = new HashSet<PyClass>();

    for (final PyFunction function : currentFunctions) {
      for (final PyClass destClass : to) {
        final PyFunctionBuilder functionBuilder = PyFunctionBuilder.copySignature(function, DECORATORS_MAY_BE_COPIED_TO_ABSTRACT);
        functionBuilder.decorate(PyNames.ABSTRACTMETHOD);
        final LanguageLevel level = LanguageLevel.forElement(destClass);
        PyClassRefactoringUtil.addMethods(destClass, functionBuilder.buildFunction(destClass.getProject(), level));
        classesToAddMetaAbc.add(destClass);
      }
    }

    // Add ABCMeta to new classes if needed
    for (final PyClass aClass : classesToAddMetaAbc) {
      if (addMetaAbcIfNeeded(aClass)) {
        filesToCheckImport.add(aClass.getContainingFile());
      }
    }

    // Add imports for ABC if needed
    for (final PsiFile file : filesToCheckImport) {
      addImportFromAbc(file, PyNames.ABSTRACTMETHOD);
      addImportFromAbc(file, ABC_META_CLASS);
      PyClassRefactoringUtil.optimizeImports(file); //To remove redundant imports
    }
  }

  /**
   * Adds metaclass = ABCMeta for class if has no.
   *
   * @param aClass class where it should be added
   * @return true if added. False if class already has metaclass so we did not touch it.
   */
  // TODO: Copy/Paste with PyClass.getMeta..
  private static boolean addMetaAbcIfNeeded(@NotNull final PyClass aClass) {
    final PsiFile file = aClass.getContainingFile();
    final PyClassLikeType type = aClass.getMetaClassType(TypeEvalContext.userInitiated(file));
    if (type != null) {
      return false; //User already has metaclass. He probably knows about metaclasses, so we should not add ABCMeta
    }
    final LanguageLevel languageLevel = LanguageLevel.forElement(aClass);
    if (languageLevel.isPy3K()) { //TODO: Copy/paste, use strategy because we already has the same check in #couldBeAbstract
      // Add (metaclass= for Py3K
      PyClassRefactoringUtil
        .addSuperClassExpressions(aClass.getProject(), aClass, null, Collections.singletonList(Pair.create(PyNames.METACLASS,
                                                                                                           ABC_META_CLASS)));
    }
    else {
      // Add __metaclass__ for Py2
      PyClassRefactoringUtil.addClassAttributeIfNotExist(aClass, PyNames.DUNDER_METACLASS, ABC_META_CLASS);
    }
    return true;
  }

  /**
   * Adds import from ABC module
   *
   * @param file         where to add import
   * @param nameToImport what to import
   */
  private static void addImportFromAbc(@NotNull final PsiFile file, @NotNull final String nameToImport) {
    AddImportHelper.addImportFromStatement(file, ABC_META_PACKAGE, nameToImport, null,
                                           AddImportHelper.ImportPriority.BUILTIN);
  }

  /**
   * Moves methods (as opposite to {@link #makeMethodsAbstract(java.util.Collection, com.jetbrains.python.psi.PyClass...)})
   *
   * @param from          source
   * @param methodsToMove what to move
   * @param to            where
   * @return newly added methods
   */
  private static List<PyElement> moveMethods(final PyClass from, final Collection<PyFunction> methodsToMove, final PyClass... to) {
    final List<PyElement> result = new ArrayList<PyElement>();
    for (final PyClass destClass : to) {
      //We move copies here because there may be several destinations
      final List<PyFunction> copies = new ArrayList<PyFunction>(methodsToMove.size());
      for (final PyFunction element : methodsToMove) {
        final PyFunction newMethod = (PyFunction)element.copy();
        copies.add(newMethod);
      }

      result.addAll(PyClassRefactoringUtil.copyMethods(copies, destClass));
    }
    deleteElements(methodsToMove);

    PyClassRefactoringUtil.insertPassIfNeeded(from);
    return result;
  }

  @NotNull
  @Override
  public PyMemberInfo<PyFunction> apply(@NotNull final PyFunction pyFunction) {
    final PyUtil.MethodFlags flags = PyUtil.MethodFlags.of(pyFunction);
    assert flags != null : "No flags return while element is function " + pyFunction;
    final boolean isStatic = flags.isStaticMethod() || flags.isClassMethod();
    return new PyMemberInfo<PyFunction>(pyFunction, isStatic, buildDisplayMethodName(pyFunction), isOverrides(pyFunction), this,
                                        couldBeAbstract(pyFunction));
  }

  /**
   * @return if method could be made abstract? (that means "create abstract version if method in parent class")
   */
  private static boolean couldBeAbstract(@NotNull final PyFunction function) {
    if (PyUtil.isInit(function)) {
      return false; // Who wants to make __init__ abstract?!
    }
    final PyUtil.MethodFlags flags = PyUtil.MethodFlags.of(function);
    assert flags != null : "Function should be called on method!";

    final boolean py3K = LanguageLevel.forElement(function).isPy3K();

    //TODO: use strategy because we already has the same check in #addMetaAbcIfNeeded
    return flags.isInstanceMethod() || py3K; //Any method could be made abstract in py3
  }


  @Nullable
  private static Boolean isOverrides(final PyFunction pyFunction) {
    final PyClass clazz = PyUtil.getContainingClassOrSelf(pyFunction);
    assert clazz != null : "Refactoring called on function, not method: " + pyFunction;
    for (final PyClass parentClass : clazz.getSuperClasses()) {
      final PyFunction parentMethod = parentClass.findMethodByName(pyFunction.getName(), true);
      if (parentMethod != null) {
        return true;
      }
    }
    return null;
  }

  @NotNull
  private static String buildDisplayMethodName(@NotNull final PyFunction pyFunction) {
    final StringBuilder builder = new StringBuilder(pyFunction.getName());
    builder.append('(');
    final PyParameter[] arguments = pyFunction.getParameterList().getParameters();
    for (final PyParameter parameter : arguments) {
      builder.append(parameter.getName());
      if (arguments.length > 1 && parameter != arguments[arguments.length - 1]) {
        builder.append(", ");
      }
    }
    builder.append(')');
    return builder.toString();
  }


  /**
   * Filters member infos to find if they should be abstracted
   */
  private static class AbstractFilter extends NotNullPredicate<PyMemberInfo<PyFunction>> {
    private final boolean myAllowAbstractOnly;

    /**
     * @param allowAbstractOnly returns only methods to be abstracted. Returns only methods to be moved otherwise.
     */
    private AbstractFilter(final boolean allowAbstractOnly) {
      myAllowAbstractOnly = allowAbstractOnly;
    }

    @Override
    protected boolean applyNotNull(@NotNull final PyMemberInfo<PyFunction> input) {
      return input.isToAbstract() == myAllowAbstractOnly;
    }
  }
}
