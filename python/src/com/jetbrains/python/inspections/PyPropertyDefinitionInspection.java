/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.inspections;

import com.google.common.collect.ImmutableList;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.Processor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.inspections.quickfix.PyUpdatePropertySignatureQuickFix;
import com.jetbrains.python.inspections.quickfix.RenameParameterQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyNoneType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Checks that arguments to property() and @property and friends are ok.
 * <br/>
 * User: dcheryasov
 * Date: Jun 30, 2010 2:53:05 PM
 */
public class PyPropertyDefinitionInspection extends PyInspection {

  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.property.definition");
  }

  private static final ImmutableList<String> SUFFIXES = ImmutableList.of(PyNames.SETTER, PyNames.DELETER);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {

    private LanguageLevel myLevel;
    private List<PyClass> myStringClasses;
    private PyFunction myOneParamFunction;
    private PyFunction myTwoParamFunction; // arglist with two args, 'self' and 'value'

    public Visitor(final ProblemsHolder holder, LocalInspectionToolSession session) {
      super(holder, session);
      PsiFile psiFile = session.getFile();
      // save us continuous checks for level, module, stc
      myLevel = LanguageLevel.forElement(psiFile);
      // string classes
      final List<PyClass> stringClasses = new ArrayList<PyClass>(2);
      final PyBuiltinCache builtins = PyBuiltinCache.getInstance(psiFile);
      PyClass cls = builtins.getClass("str");
      if (cls != null) stringClasses.add(cls);
      cls = builtins.getClass("unicode");
      if (cls != null) stringClasses.add(cls);
      myStringClasses = stringClasses;
      // reference signatures
      PyClass objectClass = builtins.getClass("object");
      if (objectClass != null) {
        final PyFunction methodRepr = objectClass.findMethodByName("__repr__", false, null);
        if (methodRepr != null) myOneParamFunction = methodRepr;
        final PyFunction methodDelattr = objectClass.findMethodByName("__delattr__", false, null);
        if (methodDelattr != null) myTwoParamFunction = methodDelattr;
      }
    }


    @Override
    public void visitPyFile(PyFile node) {
      super.visitPyFile(node);
    }

    @Override
    public void visitPyClass(final PyClass node) {
      super.visitPyClass(node);
      // check property() and @property
      node.scanProperties(new Processor<Property>() {
        @Override
        public boolean process(Property property) {
          PyTargetExpression target = property.getDefinitionSite();
          if (target != null) {
            // target = property(); args may be all funny
            PyCallExpression call = (PyCallExpression)target.findAssignedValue();
            assert call != null : "Property has a null call assigned to it";
            final PyArgumentList arglist = call.getArgumentList();
            assert arglist != null : "Property call has null arglist";
            // we assume fget, fset, fdel, doc names
            final PyCallExpression.PyArgumentsMapping mapping = call.mapArguments(getResolveContext());
            for (Map.Entry<PyExpression, PyNamedParameter> entry : mapping.getMappedParameters().entrySet()) {
              final String paramName = entry.getValue().getName();
              PyExpression argument = PyUtil.peelArgument(entry.getKey());
              checkPropertyCallArgument(paramName, argument, node.getContainingFile());
            }
          }
          else {
            // @property; we only check getter, others are checked by visitPyFunction
            // getter is always present with this form
            final PyCallable callable = property.getGetter().valueOrNull();
            if (callable instanceof PyFunction) {
              checkGetter(callable, getFunctionMarkingElement((PyFunction)callable));
            }
          }
          return false;  // always want more
        }
      }, false);
    }

    private void checkPropertyCallArgument(String paramName, PyExpression argument, PsiFile containingFile) {
      assert argument != null : "Parameter mapped to null argument";
      PyCallable callable = null;
      if (argument instanceof PyReferenceExpression) {
        final PsiPolyVariantReference reference = ((PyReferenceExpression)argument).getReference(getResolveContext());
        PsiElement resolved = reference.resolve();
        if (resolved instanceof PyCallable) {
          callable = (PyCallable)resolved;
        }
        else {
          reportNonCallableArg(resolved, argument);
          return;
        }
      }
      else if (argument instanceof PyLambdaExpression) {
        callable = (PyLambdaExpression)argument;
      }
      else if (!"doc".equals(paramName)) {
        reportNonCallableArg(argument, argument);
        return;
      }
      if (callable != null && callable.getContainingFile() != containingFile) {
        return;
      }
      if ("fget".equals(paramName)) {
        checkGetter(callable, argument);
      }
      else if ("fset".equals(paramName)) {
        checkSetter(callable, argument);
      }
      else if ("fdel".equals(paramName)) {
        checkDeleter(callable, argument);
      }
      else if ("doc".equals(paramName)) {
        PyType type = myTypeEvalContext.getType(argument);
        if (!(type instanceof PyClassType && myStringClasses.contains(((PyClassType)type).getPyClass()))) {
          registerProblem(argument, PyBundle.message("INSP.doc.param.should.be.str"));
        }
      }
    }

    private void reportNonCallableArg(PsiElement resolved, PsiElement element) {
      if (resolved instanceof PySubscriptionExpression || resolved instanceof PyNoneLiteralExpression) {
        return;
      }
      if (PyNames.NONE.equals(element.getText())) {
        return;
      }
      if (resolved instanceof PyTypedElement) {
        final PyType type = myTypeEvalContext.getType((PyTypedElement)resolved);
        final Boolean isCallable = PyTypeChecker.isCallable(type);
        if (isCallable != null && !isCallable) {
          registerProblem(element, PyBundle.message("INSP.strange.arg.want.callable"));
        }
      }
    }

    @Override
    public void visitPyFunction(PyFunction node) {
      super.visitPyFunction(node);
      if (myLevel.isAtLeast(LanguageLevel.PYTHON26)) {
        // check @foo.setter and @foo.deleter
        PyClass cls = node.getContainingClass();
        if (cls != null) {
          final PyDecoratorList decos = node.getDecoratorList();
          if (decos != null) {
            String name = node.getName();
            for (PyDecorator deco : decos.getDecorators()) {
              final QualifiedName qName = deco.getQualifiedName();
              if (qName != null) {
                List<String> nameParts = qName.getComponents();
                if (nameParts.size() == 2) {
                  final int suffixIndex = SUFFIXES.indexOf(nameParts.get(1));
                  if (suffixIndex >= 0) {
                    if (Comparing.equal(name, nameParts.get(0))) {
                      // names are ok, what about signatures?
                      PsiElement markable = getFunctionMarkingElement(node);
                      if (suffixIndex == 0) {
                        checkSetter(node, markable);
                      }
                      else {
                        checkDeleter(node, markable);
                      }
                    }
                    else {
                      registerProblem(deco, PyBundle.message("INSP.func.property.name.mismatch"));
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    @Nullable
    private static PsiElement getFunctionMarkingElement(PyFunction node) {
      if (node == null) return null;
      final ASTNode nameNode = node.getNameNode();
      PsiElement markable = node;
      if (nameNode != null) markable = nameNode.getPsi();
      return markable;
    }


    private void checkGetter(PyCallable callable, PsiElement beingChecked) {
      if (callable != null) {
        checkOneParameter(callable, beingChecked, true);
        checkReturnValueAllowed(callable, beingChecked, true, PyBundle.message("INSP.getter.return.smth"));
      }
    }

    private void checkSetter(PyCallable callable, PsiElement beingChecked) {
      if (callable != null) {
        // signature: at least two params, more optionals ok; first arg 'self'
        final PyParameterList paramList = callable.getParameterList();
        if (myTwoParamFunction != null && !PyUtil.isSignatureCompatibleTo(callable, myTwoParamFunction, myTypeEvalContext)) {
          registerProblem(beingChecked, PyBundle.message("INSP.setter.signature.advice"), new PyUpdatePropertySignatureQuickFix(true));
        }
        checkForSelf(paramList);
        // no explicit return type
        checkReturnValueAllowed(callable, beingChecked, false, PyBundle.message("INSP.setter.should.not.return"));
      }
    }

    private void checkDeleter(PyCallable callable, PsiElement beingChecked) {
      if (callable != null) {
        checkOneParameter(callable, beingChecked, false);
        checkReturnValueAllowed(callable, beingChecked, false, PyBundle.message("INSP.deleter.should.not.return"));
      }
    }

    private void checkOneParameter(PyCallable callable, PsiElement beingChecked, boolean isGetter) {
      final PyParameterList parameterList = callable.getParameterList();
      if (myOneParamFunction != null && !PyUtil.isSignatureCompatibleTo(callable, myOneParamFunction, myTypeEvalContext)) {
        if (isGetter) {
          registerProblem(beingChecked, PyBundle.message("INSP.getter.signature.advice"),
                          new PyUpdatePropertySignatureQuickFix(false));
        }
        else {
          registerProblem(beingChecked, PyBundle.message("INSP.deleter.signature.advice"), new PyUpdatePropertySignatureQuickFix(false));
        }
      }
      checkForSelf(parameterList);
    }

    private void checkForSelf(PyParameterList paramList) {
      PyParameter[] parameters = paramList.getParameters();
      final PyClass cls = PsiTreeUtil.getParentOfType(paramList, PyClass.class);
      if (cls != null && cls.isSubclass("type", null)) return;
      if (parameters.length > 0 && !PyNames.CANONICAL_SELF.equals(parameters[0].getName())) {
        registerProblem(
          parameters[0], PyBundle.message("INSP.accessor.first.param.is.$0", PyNames.CANONICAL_SELF), ProblemHighlightType.WEAK_WARNING,
          null,
          new RenameParameterQuickFix(PyNames.CANONICAL_SELF));
      }
    }

    private void checkReturnValueAllowed(PyCallable callable, PsiElement beingChecked, boolean allowed, String message) {
      // TODO: use a real flow analysis to check all exit points
      boolean hasReturns;
      if (callable instanceof PyFunction) {
        final PsiElement[] returnStatements = PsiTreeUtil.collectElements(callable, new PsiElementFilter() {
          @Override
          public boolean isAccepted(PsiElement element) {
            return (element instanceof PyReturnStatement && ((PyReturnStatement)element).getExpression() != null) ||
                   (element instanceof PyYieldExpression);
          }
        });
        hasReturns = returnStatements.length > 0;
      }
      else {
        final PyType type = myTypeEvalContext.getReturnType(callable);
        hasReturns = !(type instanceof PyNoneType);
      }
      if (allowed ^ hasReturns) {
        if (allowed && callable instanceof PyFunction) {
          if (PyUtil.isDecoratedAsAbstract(((PyFunction)callable))) {
            return;
          }
          // one last chance: maybe there's no return but a 'raise' statement, see PY-4043, PY-5048
          PyStatementList statementList = ((PyFunction)callable).getStatementList();
          for (PyStatement stmt : statementList.getStatements()) {
            if (stmt instanceof PyRaiseStatement) {
              return;
            }
          }
        }
        registerProblem(beingChecked, message);
      }
    }
  }
}
