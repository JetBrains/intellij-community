// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.codeInsight.typing.PyProtocolsKt;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyClassImpl;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.refactoring.PyPsiRefactoringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class PyAbstractClassInspection extends PyInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  private static final class Visitor extends PyInspectionVisitor {

    private Visitor(@NotNull ProblemsHolder holder,
                    @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyCallExpression(@NotNull PyCallExpression node) {
      if (node.getCallee() instanceof PyReferenceExpression calleeReferenceExpression) {
        QualifiedResolveResult resolveResult = calleeReferenceExpression.followAssignmentsChain(getResolveContext());
        if (resolveResult.getElement() instanceof PyClass pyClass) {
          if (PyClassImpl.canHaveAbstractMethods(pyClass, myTypeEvalContext)) {
            boolean hasAbstractMethod =
              ContainerUtil.exists(pyClass.getMethods(), method -> PyKnownDecoratorUtil.hasAbstractDecorator(method, myTypeEvalContext));
            if (hasAbstractMethod || !getAllSuperAbstractMethods(pyClass).isEmpty()) {
              registerProblem(node, PyPsiBundle.message("INSP.abstract.class.cannot.instantiate.abstract.class", pyClass.getName()),
                              ProblemHighlightType.WARNING);
            }
            else if (isAbstract(pyClass)) {
              registerProblem(node, PyPsiBundle.message("INSP.abstract.class.cannot.instantiate.abstract.class", pyClass.getName()));
            }
          }
        }
      }
    }

    @Override
    public void visitPyClass(@NotNull PyClass pyClass) {
      if (isAbstract(pyClass) || PyProtocolsKt.isProtocol(pyClass, myTypeEvalContext)) {
        return;
      }

      List<PyDecorator> abstractDecorators = new ArrayList<>();
      for (PyFunction method : pyClass.getMethods()) {
        PyDecoratorList decoratorList = method.getDecoratorList();
        if (decoratorList == null) continue;

        for (PyDecorator decorator : decoratorList.getDecorators()) {
          boolean isAbstract = ContainerUtil.exists(PyKnownDecoratorUtil.asKnownDecorators(decorator, myTypeEvalContext),
                                                    PyKnownDecorator::isAbstract);
          if (isAbstract) {
            abstractDecorators.add(decorator);
          }
        }
      }

      if (!PyClassImpl.canHaveAbstractMethods(pyClass, myTypeEvalContext)) {
        for (PyDecorator decorator : abstractDecorators) {
          final SmartList<LocalQuickFix> quickFixes = new SmartList<>();
          addMakeClassAbstractFixes(pyClass, quickFixes);
          registerProblem(decorator,
                          PyPsiBundle.message("INSP.abstract.class.abstract.methods.are.allowed.in.classes.whose.metaclass.is.abcmeta"),
                          quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
        }
      }

      if (abstractDecorators.isEmpty()) {
        final List<PyFunction> toImplement = getAllSuperAbstractMethods(pyClass);

        final ASTNode nameNode = pyClass.getNameNode();
        if (!toImplement.isEmpty() && nameNode != null) {
          final SmartList<LocalQuickFix> quickFixes = new SmartList<>();

          LocalQuickFix qf = PythonUiService.getInstance().createPyImplementMethodsQuickFix(pyClass, toImplement);
          if (qf != null) {
            quickFixes.add(qf);
          }
          addMakeClassAbstractFixes(pyClass, quickFixes);

          registerProblem(nameNode.getPsi(),
                          PyPsiBundle.message("INSP.abstract.class.class.must.implement.all.abstract.methods", pyClass.getName()),
                          quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
        }
      }
    }

    private boolean isAbstract(@NotNull PyClass pyClass) {
      final PyClassLikeType metaClass = pyClass.getMetaClassType(false, myTypeEvalContext);
      if (metaClass != null && PyNames.ABC_META_CLASS.equals(metaClass.getName())) {
        return true;
      }
      for (PyClassLikeType superClassType : pyClass.getSuperClassTypes(myTypeEvalContext)) {
        if (superClassType != null && PyNames.ABC.equals(superClassType.getClassQName())) {
          return true;
        }
      }
      return false;
    }

    private @NotNull List<PyFunction> getAllSuperAbstractMethods(@NotNull PyClass pyClass) {
    /* Do not report problem if class contains only methods that raise NotImplementedError without any abc.* decorators
       but keep ability to implement them via quickfix (see PY-38680) */
      return ContainerUtil.filter(PyPsiRefactoringUtil.getAllSuperAbstractMethods(pyClass, myTypeEvalContext),
                                  function -> PyKnownDecoratorUtil.hasAbstractDecorator(function, myTypeEvalContext));
    }

    private static void addMakeClassAbstractFixes(@NotNull PsiElement element, @NotNull Collection<LocalQuickFix> fixes) {
      fixes.add(new SetABCMetaAsMetaclassQuickFix());

      if (LanguageLevel.forElement(element).isPy3K()) {
        fixes.add(new AddABCToSuperclassesQuickFix());
      }
    }

    private static @Nullable PyClass getPyClass(@NotNull PsiElement element) {
      // element is a class name node
      if (element.getParent() instanceof PyClass pyClass) {
        return pyClass;
      }
      // element is a method decorator
      if (element instanceof PyDecorator decorator) {
        return Optional.ofNullable(decorator.getTarget())
          .map(PyFunction::getContainingClass)
          .orElse(null);
      }
      return null;
    }

    private static class AddABCToSuperclassesQuickFix extends PsiUpdateModCommandQuickFix {

      @Override
      public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
        return PyPsiBundle.message("INSP.abstract.class.add.to.superclasses", PyNames.ABC);
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
        final PyClass cls = getPyClass(element);
        if (cls == null) return;

        final PyClass abcClass = PyPsiFacade.getInstance(project).createClassByQName(PyNames.ABC, cls);
        if (abcClass == null) return;

        PyPsiRefactoringUtil.addSuperclasses(project, cls, abcClass);
      }
    }

    private static class SetABCMetaAsMetaclassQuickFix extends PsiUpdateModCommandQuickFix {

      @Override
      public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
        return PyPsiBundle.message("INSP.abstract.class.set.as.metaclass", PyNames.ABC_META);
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
        final PyClass cls = getPyClass(element);
        if (cls == null) return;

        final PyClass abcMetaClass = PyPsiFacade.getInstance(project).createClassByQName(PyNames.ABC_META, cls);
        if (abcMetaClass == null) return;

        final TypeEvalContext context = TypeEvalContext.userInitiated(cls.getProject(), cls.getContainingFile());
        PyPsiRefactoringUtil.addMetaClassIfNotExist(cls, abcMetaClass, context);
      }
    }
  }
}
