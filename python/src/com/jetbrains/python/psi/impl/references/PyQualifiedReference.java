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
package com.jetbrains.python.psi.impl.references;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ProcessingContext;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.*;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.search.PyProjectScopeBuilder;
import com.jetbrains.python.psi.stubs.PyClassNameIndexInsensitive;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.stubs.PyInstanceAttributeIndex;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class PyQualifiedReference extends PyReferenceImpl {
  private static final Logger LOG = Logger.getInstance(PyQualifiedReference.class);

  public PyQualifiedReference(PyQualifiedExpression element, PyResolveContext context) {
    super(element, context);
  }

  @NotNull
  @Override
  protected List<RatedResolveResult> resolveInner() {
    ResolveResultList ret = new ResolveResultList();

    final String referencedName = myElement.getReferencedName();
    if (referencedName == null) return ret;

    final PyExpression qualifier = myElement.getQualifier();
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

    // look for assignment of this attribute in containing function
    if (qualifier instanceof PyQualifiedExpression && ret.isEmpty()) {
      if (addAssignedAttributes(ret, referencedName, (PyQualifiedExpression)qualifier)) {
        return ret;
      }
    }

    if (PyTypeChecker.isUnknown(qualifierType) &&
        myContext.allowImplicits() && canQualifyAnImplicitName(qualifier, qualifierType)) {
      addImplicitResolveResults(referencedName, ret);
    }

    // special case of __doc__
    if ("__doc__".equals(referencedName)) {
      addDocReference(ret, qualifier, qualifierType);
    }
    return ret;
  }

  private static boolean isOtherClassQualifying(PyExpression qualifier, PyClassType qualifierType) {
    final List<? extends PsiElement> match = PyUtil.searchForWrappingMethod(qualifier, true);
    if (match == null) {
      return true;
    }
    if (match.size() > 1) {
      final PyClass ourClass = qualifierType.getPyClass();
      final PsiElement theirClass = CompletionUtil.getOriginalOrSelf(match.get(match.size() - 1));
      if (ourClass != theirClass) return true;
    }
    return false;
  }

  private void addImplicitResolveResults(String referencedName, ResolveResultList ret) {
    final Project project = myElement.getProject();
    final GlobalSearchScope scope = PyProjectScopeBuilder.excludeSdkTestsScope(project);
    final Collection functions = PyFunctionNameIndex.find(referencedName, project, scope);
    final PsiFile containingFile = myElement.getContainingFile();
    final List<QualifiedName> imports;
    if (containingFile instanceof PyFile) {
      imports = collectImports((PyFile)containingFile);
    }
    else {
      imports = Collections.emptyList();
    }
    for (Object function : functions) {
      if (!(function instanceof PyFunction)) {
        FileBasedIndex.getInstance().scheduleRebuild(StubUpdatingIndex.INDEX_ID,
                                                     new Throwable("found non-function object " + function + " in function list"));
        break;
      }
      PyFunction pyFunction = (PyFunction)function;
      if (pyFunction.getContainingClass() != null) {
        ret.add(new ImplicitResolveResult(pyFunction, getImplicitResultRate(pyFunction, imports)));
      }
    }

    final Collection attributes = PyInstanceAttributeIndex.find(referencedName, project, scope);
    for (Object attribute : attributes) {
      if (!(attribute instanceof PyTargetExpression)) {
        FileBasedIndex.getInstance().scheduleRebuild(StubUpdatingIndex.INDEX_ID,
                                                     new Throwable(
                                                       "found non-target expression object " + attribute + " in target expression list"));
        break;
      }
      ret.add(new ImplicitResolveResult((PyTargetExpression)attribute, getImplicitResultRate((PyTargetExpression)attribute, imports)));
    }
  }

  private static List<QualifiedName> collectImports(PyFile containingFile) {
    List<QualifiedName> imports = new ArrayList<QualifiedName>();
    for (PyFromImportStatement anImport : containingFile.getFromImports()) {
      final QualifiedName source = anImport.getImportSourceQName();
      if (source != null) {
        imports.add(source);
      }
    }
    for (PyImportElement importElement : containingFile.getImportTargets()) {
      final QualifiedName qName = importElement.getImportedQName();
      if (qName != null) {
        imports.add(qName.removeLastComponent());
      }
    }
    return imports;
  }

  private int getImplicitResultRate(PyElement target, List<QualifiedName> imports) {
    int rate = RatedResolveResult.RATE_LOW;
    if (target.getContainingFile() == myElement.getContainingFile()) {
      rate += 200;
    }
    else {
      final VirtualFile vFile = target.getContainingFile().getVirtualFile();
      if (vFile != null) {
        if (ProjectScope.getProjectScope(myElement.getProject()).contains(vFile)) {
          rate += 80;
        }
        final QualifiedName qName = QualifiedNameFinder.findShortestImportableQName(myElement, vFile);
        if (qName != null && imports.contains(qName)) {
          rate += 70;
        }
      }
    }
    if (myElement.getParent() instanceof PyCallExpression) {
      if (target instanceof PyFunction) rate += 50;
    }
    else {
      if (!(target instanceof PyFunction)) rate += 50;
    }
    return rate;
  }

  private static boolean canQualifyAnImplicitName(@NotNull PyExpression qualifier, @Nullable PyType qualType) {
    if (qualType == null) {
      if (qualifier instanceof PyCallExpression) {
        PyExpression callee = ((PyCallExpression)qualifier).getCallee();
        if (callee instanceof PyReferenceExpression && PyNames.SUPER.equals(callee.getName())) {
          PsiElement target = ((PyReferenceExpression)callee).getReference().resolve();
          if (target != null && PyBuiltinCache.getInstance(qualifier).hasInBuiltins(target)) return false; // super() of unresolved type
        }
      }
    }
    return true;
  }

  private static boolean addAssignedAttributes(ResolveResultList ret, String referencedName, PyQualifiedExpression qualifier) {
    for (PyExpression ex : collectAssignedAttributes(qualifier)) {
      if (referencedName.equals(ex.getName())) {
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

  @NotNull
  @Override
  public Object[] getVariants() {
    PyExpression qualifier = myElement.getQualifier();
    if (qualifier != null) {
      qualifier = CompletionUtil.getOriginalOrSelf(qualifier);
    }
    if (qualifier == null) {
      return EMPTY_ARRAY;
    }
    final PyQualifiedExpression element = CompletionUtil.getOriginalOrSelf(myElement);

    PyType qualifierType = TypeEvalContext.userInitiated(element.getContainingFile()).getType(qualifier);
    ProcessingContext ctx = new ProcessingContext();
    final Set<String> namesAlready = new HashSet<String>();
    ctx.put(PyType.CTX_NAMES, namesAlready);
    if (qualifierType != null) {
      Collection<Object> variants = new ArrayList<Object>();
      Collections.addAll(variants, getVariantFromHasAttr(qualifier));
      if (qualifier instanceof PyQualifiedExpression) {
        Collection<PyExpression> attrs = collectAssignedAttributes((PyQualifiedExpression)qualifier);
        for (PyExpression ex : attrs) {
          final String name = ex.getName();
          if (name != null && name.endsWith(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)) {
            continue;
          }
          if (ex instanceof PsiNamedElement && qualifierType instanceof PyClassType) {
            variants.add(LookupElementBuilder.create((PsiNamedElement)ex)
                           .withTypeText(qualifierType.getName())
                           .withIcon(PlatformIcons.FIELD_ICON));
          }
          if (ex instanceof PyReferenceExpression) {
            PyReferenceExpression refExpr = (PyReferenceExpression)ex;
            namesAlready.add(refExpr.getReferencedName());
          }
          else if (ex instanceof PyTargetExpression) {
            PyTargetExpression targetExpr = (PyTargetExpression)ex;
            namesAlready.add(targetExpr.getName());
          }
        }
        Collections.addAll(variants, qualifierType.getCompletionVariants(element.getName(), element, ctx));
        return variants.toArray();
      }
      else {
        return qualifierType.getCompletionVariants(element.getName(), element, ctx);
      }
    }
    return getUntypedVariants();
  }

  private Object[] getVariantFromHasAttr(PyExpression qualifier) {
    Collection<Object> variants = new ArrayList<Object>();
    PyIfStatement ifStatement = PsiTreeUtil.getParentOfType(myElement, PyIfStatement.class);
    while (ifStatement != null) {
      PyExpression condition = ifStatement.getIfPart().getCondition();
      if (condition instanceof PyCallExpression && ((PyCallExpression)condition).isCalleeText(PyNames.HAS_ATTR)) {
        PyCallExpression call = (PyCallExpression)condition;
        if (call.getArguments().length > 1 && call.getArguments()[0].getText().equals(qualifier.getText())) {
          PyStringLiteralExpression string = call.getArgument(1, PyStringLiteralExpression.class);
          if (string != null && StringUtil.isJavaIdentifier(string.getStringValue())) variants.add(string.getStringValue());
        }
      }
      ifStatement = PsiTreeUtil.getParentOfType(ifStatement, PyIfStatement.class);
    }
    return variants.toArray();
  }

  private Object[] getUntypedVariants() {
    final PyExpression qualifierElement = myElement.getQualifier();
    if (qualifierElement instanceof PyReferenceExpression) {
      PyReferenceExpression qualifier = (PyReferenceExpression)qualifierElement;
      final String className = qualifier.getReferencedName();
      if (className != null) {
        Collection<PyClass> classes = PyClassNameIndexInsensitive.find(className, getElement().getProject());
        classes = filterByImports(classes, myElement.getContainingFile());
        if (classes.size() == 1) {
          final PyClassType classType = new PyClassTypeImpl(classes.iterator().next(), false);
          return getTypeCompletionVariants(myElement, classType);
        }
      }
      return collectSeenMembers(qualifier.getText());
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private static Collection<PyClass> filterByImports(Collection<PyClass> classes, PsiFile containingFile) {
    if (classes.size() <= 1) {
      return classes;
    }
    List<PyClass> result = new ArrayList<PyClass>();
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

  private Object[] collectSeenMembers(final String text) {
    final Set<String> members = new HashSet<String>();
    myElement.getContainingFile().accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyReferenceExpression(PyReferenceExpression node) {
        super.visitPyReferenceExpression(node);
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
    List<LookupElement> results = new ArrayList<LookupElement>(members.size());
    for (String member : members) {
      results.add(AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(LookupElementBuilder.create(member)));
    }
    return results.toArray(new Object[results.size()]);
  }

  private static Collection<PyExpression> collectAssignedAttributes(PyQualifiedExpression qualifier) {
    QualifiedName qualifierPath = PyQualifiedNameFactory.fromReferenceChain(PyResolveUtil.unwindQualifiers(qualifier));
    if (qualifierPath != null) {
      AssignmentCollectProcessor proc = new AssignmentCollectProcessor(qualifierPath);
      PyResolveUtil.treeCrawlUp(proc, qualifier);
      return proc.getResult();
    }
    else {
      return Collections.emptyList();
    }
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
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
        assert ((StubBasedPsiElement)containingFile).getStub() == null : "Stub origin for type eval context in isReferenceTo()";
      }
      final TypeEvalContext context = TypeEvalContext.codeAnalysis(containingFile);
      resolveContext = resolveContext.withTypeEvalContext(context);
    }
    if (element instanceof PyFunction && Comparing.equal(referencedName, ((PyFunction)element).getName()) &&
        ((PyFunction)element).getContainingClass() != null && !PyNames.INIT.equals(referencedName)) {
      final PyExpression qualifier = myElement.getQualifier();
      if (qualifier != null) {
        final PyType qualifierType = resolveContext.getTypeEvalContext().getType(qualifier);
        if (qualifierType == null) {
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

  @NotNull
  protected PyQualifiedReference copyWithResolveContext(PyResolveContext context) {
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
        element instanceof PyTargetExpression && PyUtil.isAttribute((PyTargetExpression)element) && Comparing.equal(
      ((PyTargetExpression)resolveResult).getReferencedName(),
      ((PyTargetExpression)element).getReferencedName())) {
      PyClass aClass = PsiTreeUtil.getParentOfType(resolveResult, PyClass.class);
      PyClass bClass = PsiTreeUtil.getParentOfType(element, PyClass.class);

      if (isSubclass(aClass, bClass)
          || (isSubclass(bClass, aClass))) {
        return true;
      }
    }

    if (resolvesToWrapper(element, resolveResult)) {
      return true;
    }
    return false;
  }

  private static boolean isSubclass(@Nullable PyClass aClass, @Nullable PyClass bClass) {
    if (aClass == null || bClass == null) {
      return false;
    }
    return bClass.isSubclass(aClass);
  }

  private static boolean isLocalScope(PsiElement element) {
    if (element instanceof PyParameter) {
      return true;
    }
    if (element instanceof PyTargetExpression) {
      return ((PyTargetExpression)element).getQualifier() == null &&
             PsiTreeUtil.getParentOfType(element, ScopeOwner.class) instanceof PyFunction;
    }
    return false;
  }

  @Override
  public String toString() {
    return "PyQualifiedReference(" + myElement + "," + myContext + ")";
  }
}
