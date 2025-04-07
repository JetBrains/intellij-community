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
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.refactoring.PyPsiRefactoringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public final class PyAbstractClassInspection extends PyInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  private static final class Visitor extends PyInspectionVisitor {

    private static @Nls @NotNull String canNotInstantiateAbstractClassMessage(@NotNull PyClass pyClass) {
      return PyPsiBundle.message("INSP.abstract.class.cannot.instantiate.abstract.class", pyClass.getName());
    }

    private Visitor(@NotNull ProblemsHolder holder,
                    @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyCallExpression(@NotNull PyCallExpression node) {
      if (node.getCallee() instanceof PyReferenceExpression calleeReferenceExpression) {
        QualifiedResolveResult resolveResult = calleeReferenceExpression.followAssignmentsChain(getResolveContext());
        if (resolveResult.getElement() instanceof PyClass pyClass) {
          if (canHaveAbstractMethods(pyClass)) {
            if (hasAbstractMethod(pyClass) || !getAllSuperAbstractMethods(pyClass).isEmpty()) {
              registerProblem(node, canNotInstantiateAbstractClassMessage(pyClass), ProblemHighlightType.WARNING);
            }
            else if (isAbstract(pyClass)) {
              registerProblem(node, canNotInstantiateAbstractClassMessage(pyClass));
            }
          }
        }
      }
    }

    @Override
    public void visitPyClass(@NotNull PyClass pyClass) {
      if (isAbstract(pyClass) || hasAbstractMethod(pyClass) || PyProtocolsKt.isProtocol(pyClass, myTypeEvalContext)) {
        return;
      }

      final List<PyFunction> toImplement = getAllSuperAbstractMethods(pyClass);

      final ASTNode nameNode = pyClass.getNameNode();
      if (!toImplement.isEmpty() && nameNode != null) {
        final SmartList<LocalQuickFix> quickFixes = new SmartList<>();

        LocalQuickFix qf = PythonUiService.getInstance().createPyImplementMethodsQuickFix(pyClass, toImplement);
        if (qf != null) {
          quickFixes.add(qf);
        }
        quickFixes.add(new SetABCMetaAsMetaclassQuickFix());

        if (LanguageLevel.forElement(pyClass).isPy3K()) {
          quickFixes.add(new AddABCToSuperclassesQuickFix());
        }

        registerProblem(nameNode.getPsi(),
                        PyPsiBundle.message("INSP.abstract.class.class.must.implement.all.abstract.methods", pyClass.getName()),
                        quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      }
    }

    private boolean canHaveAbstractMethods(@NotNull PyClass pyClass) {
      PyClassLikeType metaClassType = pyClass.getMetaClassType(true, myTypeEvalContext);
      if (metaClassType != null && PyNames.ABC_META.equals(metaClassType.getClassQName())) {
        return true;
      }
      return pyClass.getAncestorTypes(myTypeEvalContext).stream()
        .filter(Objects::nonNull)
        .map(PyClassLikeType::getClassQName)
        .anyMatch(qName -> PyTypingTypeProvider.PROTOCOL.equals(qName) || PyTypingTypeProvider.PROTOCOL_EXT.equals(qName));
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

    private boolean hasAbstractMethod(@NotNull PyClass pyClass) {
      for (PyFunction method : pyClass.getMethods()) {
        if (PyKnownDecoratorUtil.hasAbstractDecorator(method, myTypeEvalContext)) {
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

    private static class AddABCToSuperclassesQuickFix extends PsiUpdateModCommandQuickFix {

      @Override
      public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
        return PyPsiBundle.message("INSP.abstract.class.add.to.superclasses", PyNames.ABC);
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
        final PyClass cls = PyUtil.as(element.getParent(), PyClass.class);
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
        final PyClass cls = PyUtil.as(element.getParent(), PyClass.class);
        if (cls == null) return;

        final PyClass abcMetaClass = PyPsiFacade.getInstance(project).createClassByQName(PyNames.ABC_META, cls);
        if (abcMetaClass == null) return;

        final TypeEvalContext context = TypeEvalContext.userInitiated(cls.getProject(), cls.getContainingFile());
        PyPsiRefactoringUtil.addMetaClassIfNotExist(cls, abcMetaClass, context);
      }
    }
  }
}
