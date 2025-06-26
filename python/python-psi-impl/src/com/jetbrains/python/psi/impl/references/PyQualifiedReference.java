/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.psi.impl.references;

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionUtilCoreImpl;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.ui.IconManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.ResolveResultList;
import com.jetbrains.python.psi.impl.references.hasattr.PyHasAttrHelper;
import com.jetbrains.python.psi.resolve.ImplicitResolveResult;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.stubs.PyClassNameIndexInsensitive;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.pyi.PyiUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public class PyQualifiedReference extends PyReferenceImpl {
  private static final Logger LOG = Logger.getInstance(PyQualifiedReference.class);

  public PyQualifiedReference(PyQualifiedExpression element, PyResolveContext context) {
    super(element, context);
  }

  @Override
  protected @NotNull List<RatedResolveResult> resolveInner() {
    PyPsiUtils.assertValid(myElement);
    ResolveResultList ret = new ResolveResultList();

    final String referencedName = myElement.getReferencedName();
    if (referencedName == null) return ret;

    final PyExpression qualifier = myElement.getQualifier();
    PyPsiUtils.assertValid(qualifier);
    if (qualifier == null) {
      return ret;
    }

    // regular attributes
    PyType qualifierType = myContext.getTypeEvalContext().getType(qualifier);
    // is it a class-private name qualified by a different class?
    if (PyUtil.isClassPrivateName(referencedName) && qualifierType instanceof PyClassType) {
      if (isOtherClassQualifying(qualifier, (PyClassType)qualifierType)) return Collections.emptyList();
    }
    //
    if (qualifierType != null) {
      qualifierType.assertValid("qualifier: " + qualifier);
      // resolve within the type proper
      AccessDirection ctx = AccessDirection.of(myElement);
      final List<? extends RatedResolveResult> membersOfQualifier = qualifierType.resolveMember(referencedName, qualifier, ctx, myContext);
      if (membersOfQualifier == null) {
        return ret; // qualifier is positive that such name cannot exist in it
      }
      ret.addAll(membersOfQualifier);
    }

    if (myContext.getTypeEvalContext().maySwitchToAST(myElement)) {
      // look for assignment of this attribute in containing function
      // See PyResolveTest#testAttributeAssignedNearby for an example
      if (qualifier instanceof PyQualifiedExpression && ret.isEmpty()) {
        if (addAssignedAttributes(ret, referencedName, (PyQualifiedExpression)qualifier)) {
          return ret;
        }
      }
    }

    if ((PyTypeChecker.isUnknown(qualifierType, myContext.getTypeEvalContext()) ||
         (qualifierType instanceof PyStructuralType && ((PyStructuralType)qualifierType).isInferredFromUsages())) &&
        myContext.allowImplicits() && PyCallExpressionHelper.canQualifyAnImplicitName(qualifier)) {
      PyResolveUtil.addImplicitResolveResults(referencedName, ret, myElement);
    }

    // special case of __doc__
    if ("__doc__".equals(referencedName)) {
      addDocReference(ret, qualifier, qualifierType);
    }

    return ret;
  }

  private static boolean isOtherClassQualifying(@NotNull PyExpression qualifier, @NotNull PyClassType qualifierType) {
    final List<? extends PsiElement> match = PyUtil.searchForWrappingMethod(qualifier, true);
    if (match == null) {
      return true;
    }
    if (match.size() > 1) {
      final PyClass ourClass = PyiUtil.getOriginalElementOrLeaveAsIs(qualifierType.getPyClass(), PyClass.class);
      final PsiElement theirClass = CompletionUtilCoreImpl.getOriginalOrSelf(match.get(match.size() - 1));
      if (ourClass != theirClass) return true;
    }
    return false;
  }

  private static boolean addAssignedAttributes(ResolveResultList ret,
                                               String referencedName,
                                               final @NotNull PyQualifiedExpression qualifier) {
    final QualifiedName qName = qualifier.asQualifiedName();
    if (qName == null) {
      return false;
    }
    for (PyExpression ex : collectAssignedAttributes(qName, qualifier)) {
      if (referencedName.equals(ex.getName()) && !PyUtil.isInstanceAttribute(ex)) {
        ret.poke(ex, RatedResolveResult.RATE_NORMAL);
        return true;
      }
    }
    return false;
  }

  private void addDocReference(ResolveResultList ret, PyExpression qualifier, PyType qualifierType) {
    PsiElement docstring = null;
    if (qualifierType instanceof PyClassType) {
      PyClass qualClass = ((PyClassType)qualifierType).getPyClass();
      docstring = qualClass.getDocStringExpression();
    }
    else if (qualifierType instanceof PyModuleType) {
      PyFile qualModule = ((PyModuleType)qualifierType).getModule();
      docstring = qualModule.getDocStringExpression();
    }
    else if (qualifier instanceof PyReferenceExpression) {
      PsiElement qual_object = ((PyReferenceExpression)qualifier).getReference(myContext).resolve();
      if (qual_object instanceof PyDocStringOwner) {
        docstring = ((PyDocStringOwner)qual_object).getDocStringExpression();
      }
    }
    ret.poke(docstring, RatedResolveResult.RATE_HIGH);
  }

  @Override
  public Object @NotNull [] getVariants() {
    PyExpression qualifier = myElement.getQualifier();
    if (qualifier != null) {
      qualifier = CompletionUtilCoreImpl.getOriginalOrSelf(qualifier);
    }
    if (qualifier == null) {
      return EMPTY_ARRAY;
    }
    final PyQualifiedExpression element = CompletionUtilCoreImpl.getOriginalOrSelf(myElement);

    PyType qualifierType = TypeEvalContext.codeCompletion(element.getProject(), element.getContainingFile()).getType(qualifier);
    ProcessingContext ctx = new ProcessingContext();
    final Set<String> namesAlready = new HashSet<>();
    ctx.put(PyType.CTX_NAMES, namesAlready);
    final Collection<Object> variants = new ArrayList<>();
    if (qualifierType != null) {
      if (qualifierType instanceof PyStructuralType && ((PyStructuralType)qualifierType).isInferredFromUsages()) {
        final PyClassType guessedType = guessClassTypeByName();
        if (guessedType != null) {
          Collections.addAll(variants, guessedType.getCompletionVariants(myElement.getName(), myElement, ctx));
        }
      }
      if (qualifier instanceof PyQualifiedExpression qualifierExpression) {
        final QualifiedName qualifiedName = qualifierExpression.asQualifiedName();
        if (qualifiedName != null) {
          final Collection<PyTargetExpression> attrs = collectAssignedAttributes(qualifiedName, qualifier);
          for (PyTargetExpression expression : attrs) {
            final String name = expression.getName();
            if (name != null && name.endsWith(CompletionInitializationContext.DUMMY_IDENTIFIER.trim())) {
              continue;
            }
            if (qualifierType instanceof PyClassType && name != null) {
              variants.add(LookupElementBuilder.createWithSmartPointer(name, expression)
                             .withTypeText(qualifierType.getName())
                             .withIcon(IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Field)));
              namesAlready.add(name);
            }
          }
        }
        Collections.addAll(variants, qualifierType.getCompletionVariants(element.getName(), element, ctx));
      }
      else {
        return qualifierType.getCompletionVariants(element.getName(), element, ctx);
      }
    }
    else {
      final PyClassType guessedType = guessClassTypeByName();
      if (guessedType != null) {
        Collections.addAll(variants, guessedType.getCompletionVariants(myElement.getName(), myElement, ctx));
      }
      if (qualifier instanceof PyReferenceExpression) {
        Collections.addAll(variants, collectSeenMembers(qualifier.getText(), ctx));
      }
    }

    StreamEx.of(PyHasAttrHelper.INSTANCE.getNamesFromHasAttrs(element, qualifier))
      .filter(it -> namesAlready.add(it))
      .into(variants);

    return variants.toArray();
  }

  private @Nullable PyClassType guessClassTypeByName() {
    final PyExpression qualifierElement = myElement.getQualifier();
    if (qualifierElement instanceof PyReferenceExpression qualifier) {
      final String className = qualifier.getReferencedName();
      if (className != null) {
        Collection<PyClass> classes = PyClassNameIndexInsensitive.find(className, getElement().getProject());
        classes = filterByImports(classes, myElement.getContainingFile());
        if (classes.size() == 1) {
          return new PyClassTypeImpl(classes.iterator().next(), false);
        }
      }
    }
    return null;
  }

  private static Collection<PyClass> filterByImports(Collection<PyClass> classes, PsiFile containingFile) {
    if (classes.size() <= 1) {
      return classes;
    }
    List<PyClass> result = new ArrayList<>();
    for (PyClass pyClass : classes) {
      if (pyClass.getContainingFile() == containingFile) {
        result.add(pyClass);
      }
      else {
        final PsiElement exportedClass = ((PyFile)containingFile).getElementNamed(pyClass.getName());
        if (exportedClass == pyClass) {
          result.add(pyClass);
        }
      }
    }
    return result;
  }

  private Object[] collectSeenMembers(final String text, ProcessingContext context) {
    final Set<String> members = new HashSet<>();
    myElement.getContainingFile().accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyReferenceExpression(@NotNull PyReferenceExpression node) {
        super.visitPyReferenceExpression(node);
        visitPyQualifiedExpression(node);
      }

      @Override
      public void visitPyTargetExpression(@NotNull PyTargetExpression node) {
        super.visitPyTargetExpression(node);
        visitPyQualifiedExpression(node);
      }

      private void visitPyQualifiedExpression(PyQualifiedExpression node) {
        if (node != myElement) {
          final PyExpression qualifier = node.getQualifier();
          if (qualifier != null && qualifier.getText().equals(text)) {
            final String refName = node.getReferencedName();
            if (refName != null) {
              members.add(refName);
            }
          }
        }
      }
    });
    List<LookupElement> results = new ArrayList<>(members.size());
    final Set<String> namesAlready = visitedNames(context);
    for (String member : members) {
      results.add(AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(LookupElementBuilder.create(member)));
      namesAlready.add(member);
    }
    return ArrayUtil.toObjectArray(results);
  }

  private static @NotNull Set<String> visitedNames(@NotNull ProcessingContext context) {
    Set<String> names = context.get(PyType.CTX_NAMES);
    if (names == null) {
      names = new HashSet<>();
      context.put(PyType.CTX_NAMES, names);
    }
    return names;
  }

  /**
   * Returns expressions accessible from scope of "anchor" with names that start with  provided "qualifierQName".
   * Can be used for completion.
   */
  public static @NotNull Collection<PyTargetExpression> collectAssignedAttributes(final @NotNull QualifiedName qualifierQName,
                                                                         final @NotNull PsiElement anchor) {
    final Set<String> names = new HashSet<>();
    final List<PyTargetExpression> results = new ArrayList<>();
    for (ScopeOwner owner = ScopeUtil.getScopeOwner(anchor); owner != null; owner = ScopeUtil.getScopeOwner(owner)) {
      final Scope scope = ControlFlowCache.getScope(owner);
      for (final PyTargetExpression target : scope.getTargetExpressions()) {
        final QualifiedName targetQName = target.asQualifiedName();
        if (targetQName != null) {
          if (targetQName.getComponentCount() == qualifierQName.getComponentCount() + 1 && targetQName.matchesPrefix(qualifierQName)) {
            final String name = target.getName();
            if (!names.contains(name)) {
              names.add(name);
              results.add(target);
            }
          }
        }
      }
    }
    return results;
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    // performance: a qualified reference can never resolve to a local variable or parameter
    if (isLocalScope(element)) {
      return false;
    }
    final String referencedName = myElement.getReferencedName();
    PyResolveContext resolveContext = myContext.withoutImplicits();
    // Guess type eval context origin for switching to local dataflow and return type analysis
    if (resolveContext.getTypeEvalContext().getOrigin() == null) {
      final PsiFile containingFile = myElement.getContainingFile();
      if (containingFile instanceof StubBasedPsiElement) {
        assert ((StubBasedPsiElement<?>)containingFile).getStub() == null : "Stub origin for type eval context in isReferenceTo()";
      }
      final TypeEvalContext context = TypeEvalContext.codeAnalysis(containingFile.getProject(), containingFile);
      resolveContext = resolveContext.withTypeEvalContext(context);
    }
    PyElement pyElement = ObjectUtils.tryCast(element, PyElement.class);
    if (pyElement == null) {
      return false;
    }
    if (Objects.equals(referencedName, pyElement.getName()) && !PyUtil.isConstructorLikeMethod(element)) {
      final PyExpression qualifier = myElement.getQualifier();
      if (qualifier != null) {
        final PyType qualifierType = resolveContext.getTypeEvalContext().getType(qualifier);
        if (qualifierType == null ||
            (qualifierType instanceof PyStructuralType && ((PyStructuralType)qualifierType).isInferredFromUsages())) {
          return true;
        }
      }
    }
    for (ResolveResult result : copyWithResolveContext(resolveContext).multiResolve(false)) {
      LOG.assertTrue(!(result instanceof ImplicitResolveResult));
      PsiElement resolveResult = result.getElement();
      if (isResolvedToResult(element, resolveResult)) {
        return true;
      }
    }

    return false;
  }

  protected @NotNull PyQualifiedReference copyWithResolveContext(PyResolveContext context) {
    return new PyQualifiedReference(myElement, context);
  }

  private boolean isResolvedToResult(PsiElement element, PsiElement resolveResult) {
    if (resolveResult instanceof PyImportedModule) {
      resolveResult = resolveResult.getNavigationElement();
    }
    if (element instanceof PsiDirectory && resolveResult instanceof PyFile &&
        PyNames.INIT_DOT_PY.equals(((PyFile)resolveResult).getName()) && ((PyFile)resolveResult).getContainingDirectory() == element) {
      return true;
    }
    if (resolveResult == element) {
      return true;
    }
    if (resolveResult instanceof PyTargetExpression && PyUtil.isAttribute((PyTargetExpression)resolveResult) &&
        element instanceof PyTargetExpression && PyUtil.isAttribute((PyTargetExpression)element) &&
        Objects.equals(((PyTargetExpression)resolveResult).getReferencedName(), ((PyTargetExpression)element).getReferencedName())) {
      PyClass aClass = PsiTreeUtil.getParentOfType(resolveResult, PyClass.class);
      PyClass bClass = PsiTreeUtil.getParentOfType(element, PyClass.class);

      if (aClass != null && bClass != null && bClass.isSubclass(aClass, myContext.getTypeEvalContext())) {
        return true;
      }
    }

    if (resolvesToWrapper(element, resolveResult)) {
      return true;
    }
    return false;
  }

  private static boolean isLocalScope(PsiElement element) {
    if (element instanceof PyParameter) {
      return true;
    }
    if (element instanceof PyTargetExpression target) {
      return !target.isQualified() && ScopeUtil.getScopeOwner(target) instanceof PyFunction;
    }
    return false;
  }

  @Override
  public String toString() {
    return "PyQualifiedReference(" + myElement + "," + myContext + ")";
  }
}
