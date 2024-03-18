// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.classes.membersManager;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.FluentIterable;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.NotNullPredicate;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.codeInsight.imports.AddImportHelper.ImportPriority;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.refactoring.PyPsiRefactoringUtil;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.refactoring.classes.PyDependenciesComparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Plugin that moves class methods
 *
 * @author Ilya.Kazakevich
 */
class MethodsManager extends MembersManager<PyFunction> {

  /**
   * Some decorators should be copied with methods if method is marked abstract. Here is list.
   */
  private static final String[] DECORATORS_MAY_BE_COPIED_TO_ABSTRACT =
    {PyNames.PROPERTY, PyNames.CLASSMETHOD, PyNames.STATICMETHOD};

  public static final String ABC_META_PACKAGE = "abc";
  private static final NoPropertiesPredicate NO_PROPERTIES = new NoPropertiesPredicate();

  MethodsManager() {
    super(PyFunction.class);
  }

  @Override
  public boolean hasConflict(@NotNull final PyFunction member, @NotNull final PyClass aClass) {
    return NamePredicate.hasElementWithSameName(member, Arrays.asList(aClass.getMethods()));
  }

  @NotNull
  @Override
  protected Collection<PyElement> getDependencies(@NotNull final MultiMap<PyClass, PyElement> usedElements) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  protected MultiMap<PyClass, PyElement> getDependencies(@NotNull final PyElement member) {
    final MyPyRecursiveElementVisitor visitor = new MyPyRecursiveElementVisitor();
    member.accept(visitor);
    return visitor.myResult;
  }

  @NotNull
  @Override
  protected List<? extends PyElement> getMembersCouldBeMoved(@NotNull final PyClass pyClass) {
    return FluentIterable.from(Arrays.asList(pyClass.getMethods())).filter(new NamelessFilter<>()).filter(NO_PROPERTIES).toList();
  }

  @Override
  protected Collection<PyElement> moveMembers(@NotNull final PyClass from,
                                              @NotNull final Collection<PyMemberInfo<PyFunction>> members,
                                              final PyClass @NotNull ... to) {
    final Collection<PyFunction> methodsToMove = fetchElements(Collections2.filter(members, new AbstractFilter(false)));
    final Collection<PyFunction> methodsToAbstract = fetchElements(Collections2.filter(members, new AbstractFilter(true)));

    makeMethodsAbstract(methodsToAbstract, to);
    return moveMethods(from, methodsToMove, true, to);
  }

  /**
   * Creates abstract version of each method in each class (does not touch method itself as opposite to {@link #moveMethods(PyClass, Collection, boolean, PyClass...)})
   *
   * @param currentFunctions functions to make them abstract
   * @param to               classes where abstract method should be created
   */
  private static void makeMethodsAbstract(final Collection<PyFunction> currentFunctions, final PyClass... to) {
    final Set<PsiFile> filesToCheckImport = new HashSet<>();
    final Set<PyClass> classesToAddMetaAbc = new HashSet<>();

    for (final PyFunction function : currentFunctions) {
      for (final PyClass destClass : to) {
        final PyFunctionBuilder functionBuilder = PyFunctionBuilder.copySignature(function, DECORATORS_MAY_BE_COPIED_TO_ABSTRACT);
        functionBuilder.decorate(PyNames.ABSTRACTMETHOD);
        PyFunction abstractMethod = functionBuilder.buildFunction();
        PyDependenciesComparator.copyDependencyInfo(function, abstractMethod);
        PyClassRefactoringUtil.addMethods(destClass, false, abstractMethod);
        classesToAddMetaAbc.add(destClass);
      }
    }

    // Add ABCMeta to new classes if needed
    for (final PyClass aClass : classesToAddMetaAbc) {
      final Project project = aClass.getProject();
      final PsiFile file = aClass.getContainingFile();

      final PyClass abcMetaClass = PyPsiFacade.getInstance(project).createClassByQName(PyNames.ABC_META, aClass);
      final TypeEvalContext context = TypeEvalContext.userInitiated(project, file);

      if (abcMetaClass != null && PyPsiRefactoringUtil.addMetaClassIfNotExist(aClass, abcMetaClass, context)) {
        filesToCheckImport.add(file);
      }
    }

    // Add imports for ABC if needed
    for (final PsiFile file : filesToCheckImport) {
      AddImportHelper.addOrUpdateFromImportStatement(file, ABC_META_PACKAGE, PyNames.ABSTRACTMETHOD, null, ImportPriority.BUILTIN, null);
      PyClassRefactoringUtil.optimizeImports(file); //To remove redundant imports
    }
  }

  /**
   * Moves methods (as opposite to {@link #makeMethodsAbstract(Collection, PyClass...)})
   *
   * @param from          source
   * @param methodsToMove what to move
   * @param to            where
   * @param skipIfExist skip (do not add) if method already exists
   * @return newly added methods
   */
  static List<PyElement> moveMethods(@NotNull PyClass from,
                                     @NotNull Collection<? extends PyFunction> methodsToMove,
                                     boolean skipIfExist,
                                     PyClass @NotNull... to) {
    if (methodsToMove.isEmpty() || to.length == 0) return Collections.emptyList();
    final List<PyElement> result = new ArrayList<>();
    for (PyClass destClass : to) {
      //We move copies here because there may be several destinations
      PyFunction[] copies = ContainerUtil.map2Array(methodsToMove, PyFunction.EMPTY_ARRAY, m -> (PyFunction)m.copy());
      result.addAll(PyClassRefactoringUtil.addMethods(destClass, skipIfExist, copies));
    }
    deleteElements(methodsToMove);
    return result;
  }

  @NotNull
  @Override
  public PyMemberInfo<PyFunction> apply(@NotNull final PyFunction pyFunction) {
    final PyUtil.MethodFlags flags = PyUtil.MethodFlags.of(pyFunction);
    assert flags != null : "No flags return while element is function " + pyFunction;
    final boolean isStatic = flags.isStaticMethod() || flags.isClassMethod();
    return new PyMemberInfo<>(pyFunction, isStatic, buildDisplayMethodName(pyFunction), isOverrides(pyFunction), this,
                              couldBeAbstract(pyFunction));
  }

  /**
   * @return if method could be made abstract? (that means "create abstract version if method in parent class")
   */
  private static boolean couldBeAbstract(@NotNull final PyFunction function) {
    if (PyUtil.isInitMethod(function)) {
      return false; // Who wants to make __init__ abstract?!
    }
    final PyUtil.MethodFlags flags = PyUtil.MethodFlags.of(function);
    assert flags != null : "Function should be called on method!";

    final boolean py3K = LanguageLevel.forElement(function).isPy3K();
    return flags.isInstanceMethod() || py3K; //Any method could be made abstract in py3
  }


  @Nullable
  private static Boolean isOverrides(final PyFunction pyFunction) {
    final PyClass clazz = PyUtil.getContainingClassOrSelf(pyFunction);
    assert clazz != null : "Refactoring called on function, not method: " + pyFunction;
    for (final PyClass parentClass : clazz.getSuperClasses(null)) {
      final PyFunction parentMethod = parentClass.findMethodByName(pyFunction.getName(), true, null);
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
  private static final class AbstractFilter extends NotNullPredicate<PyMemberInfo<PyFunction>> {
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

  private static class MyPyRecursiveElementVisitor extends PyRecursiveElementVisitorWithResult {
    @Override
    public void visitPyCallExpression(final @NotNull PyCallExpression node) {
      // TODO: refactor, messy code
      final PyExpression callee = node.getCallee();
      if (callee != null) {
        final PsiReference calleeRef = callee.getReference();
        if (calleeRef != null) {
          final PsiElement calleeDeclaration = calleeRef.resolve();
          if (calleeDeclaration instanceof PyFunction calleeFunction) {
            final PyClass clazz = calleeFunction.getContainingClass();
            if (clazz != null) {
              if (PyUtil.isInitMethod(calleeFunction)) {
                return; // Init call should not be marked as dependency
              }
              myResult.putValue(clazz, calleeFunction);
            }
          }
        }
      }
    }
  }

  /**
   * Filter out property setters and getters
   */
  private static class NoPropertiesPredicate implements Predicate<PyFunction> {
    @Override
    public boolean apply(@NotNull PyFunction input) {
      return input.getProperty() == null;
    }
  }
}
