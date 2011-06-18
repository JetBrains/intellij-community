package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.patterns.SyntaxMatchers;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.stubs.PyClassNameIndexInsensitive;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class PyQualifiedReferenceImpl extends PyReferenceImpl {
  public PyQualifiedReferenceImpl(PyQualifiedExpression element, PyResolveContext context) {
    super(element, context);
  }

  @NotNull
  @Override
  protected List<RatedResolveResult> resolveInner() {
    ResolveResultList ret = new ResolveResultList();

    final String referencedName = myElement.getReferencedName();
    if (referencedName == null) return ret;

    final PyExpression qualifier = myElement.getQualifier();
    assert qualifier != null;

    // regular attributes
    PyType qualifierType = myContext.getTypeEvalContext().getType(qualifier);
    // is it a class-private name qualified by a different class?
    if (PyUtil.isClassPrivateName(referencedName) && qualifierType instanceof PyClassType) {
      final List<? extends PsiElement> match = SyntaxMatchers.DEEP_IN_METHOD.search(qualifier);
      if (match == null || (match.size() > 1 && ((PyClassType)qualifierType).getPyClass() != match.get(match.size() - 1))) {
        return Collections.emptyList();
      }
    }
    //
    if (qualifierType != null && !(qualifierType instanceof PyTypeReference)) {
      // resolve within the type proper
      AccessDirection ctx = AccessDirection.of(myElement);
      final List<? extends RatedResolveResult> membersOfQualifier = qualifierType.resolveMember(referencedName, qualifier, ctx, myContext);
      if (membersOfQualifier == null) {
        return ret; // qualifier is positive that such name cannot exist in it
      }
      ret.addAll(membersOfQualifier);

      // enrich the type info with any fields assigned nearby
      if (qualifier instanceof PyQualifiedExpression && ret.isEmpty()) {
        if (addAssignedAttributes(ret, referencedName, qualifier)) return ret;
      }
    }
    else if (myContext.allowImplicits() && canQualifyAnImplicitName(qualifier, qualifierType)) {
      final Collection functions = PyFunctionNameIndex.find(referencedName, myElement.getProject());
      for (Object function : functions) {
        if (!(function instanceof PyFunction)) {
          FileBasedIndex.getInstance().scheduleRebuild(StubUpdatingIndex.INDEX_ID,
                                                       new Throwable("found non-function object " + function + " in function list"));
          break;
        }
        PyFunction pyFunction = (PyFunction) function;
        if (pyFunction.getContainingClass() != null) {
          ret.add(new ImplicitResolveResult(pyFunction));
        }
      }
    }
    // special case of __doc__
    if ("__doc__".equals(referencedName)) {
      addDocReference(ret, qualifier, qualifierType);
    }
    return ret;
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

  private static boolean addAssignedAttributes(ResolveResultList ret, String referencedName, PyExpression qualifier) {
    List<PyQualifiedExpression> qualifier_path = PyResolveUtil.unwindQualifiers((PyQualifiedExpression)qualifier);
    if (qualifier_path != null) {
      for (PyExpression ex : collectAssignedAttributes((PyQualifiedExpression)qualifier)) {
        if (referencedName.equals(ex.getName())) {
          ret.poke(ex, RatedResolveResult.RATE_NORMAL);
          return true;
        }
      }
    }
    return false;
  }

  private void addDocReference(ResolveResultList ret, PyExpression qualifier, PyType qualifierType) {
    PsiElement docstring = null;
    if (qualifierType instanceof PyClassType) {
      PyClass qual_class = ((PyClassType)qualifierType).getPyClass();
      if (qual_class != null) docstring = qual_class.getDocStringExpression();
    }
    else if (qualifierType instanceof PyModuleType) {
      PsiFile qual_module = ((PyModuleType)qualifierType).getModule();
      if (qual_module instanceof PyDocStringOwner) {
        docstring = ((PyDocStringOwner)qual_module).getDocStringExpression();
      }
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
    assert qualifier != null;

    PyType qualifierType = qualifier.getType(TypeEvalContext.slow());
    ProcessingContext ctx = new ProcessingContext();
    final Set<String> namesAlready = new HashSet<String>();
    ctx.put(PyType.CTX_NAMES, namesAlready);
    if (qualifierType != null) {
      Collection<Object> variants = new ArrayList<Object>();
      if (qualifier instanceof PyQualifiedExpression) {
        Collection<PyExpression> attrs = collectAssignedAttributes((PyQualifiedExpression)qualifier);
        variants.addAll(attrs);
        for (PyExpression ex : attrs) {
          if (ex instanceof PyReferenceExpression) {
            PyReferenceExpression refExpr = (PyReferenceExpression)ex;
            namesAlready.add(refExpr.getReferencedName());
          }
          else if (ex instanceof PyTargetExpression) {
            PyTargetExpression targetExpr = (PyTargetExpression)ex;
            namesAlready.add(targetExpr.getName());
          }
        }
        Collections.addAll(variants, qualifierType.getCompletionVariants(myElement.getName(), myElement, ctx));
        return variants.toArray();
      }
      else {
        return qualifierType.getCompletionVariants(myElement.getName(), myElement, ctx);
      }
    }
    return getUntypedVariants();
  }

  private Object[] getUntypedVariants() {
    final PyExpression qualifierElement = myElement.getQualifier();
    if (qualifierElement instanceof PyReferenceExpression) {
      PyReferenceExpression qualifier = (PyReferenceExpression)qualifierElement;
      if (qualifier.getQualifier() == null) {
        final String className = qualifier.getText();
        Collection<PyClass> classes = PyClassNameIndexInsensitive.find(className, getElement().getProject());
        classes = filterByImports(classes, myElement.getContainingFile());
        if (classes.size() == 1) {
          final PyClassType classType = new PyClassType(classes.iterator().next(), false);
          return getTypeCompletionVariants(myElement, classType);
        }
        return collectSeenMembers(qualifier.getText());
      }
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
        final PsiElement exportedClass = ((PyFile)containingFile).findExportedName(pyClass.getName());
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
    List<String> qualifier_path = PyResolveUtil.unwindQualifiersAsStrList(qualifier);
    if (qualifier_path != null) {
      AssignmentCollectProcessor proc = new AssignmentCollectProcessor(qualifier_path);
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

    if (resolve() == element) {
      return true;
    }
    final String referencedName = myElement.getReferencedName();
    if (element instanceof PyFunction && Comparing.equal(referencedName, ((PyFunction)element).getName()) &&
        ((PyFunction)element).getContainingClass() != null && !PyNames.INIT.equals(referencedName)) {
      final PyExpression qualifier = myElement.getQualifier();
      if (qualifier != null) {
        final TypeEvalContext context = TypeEvalContext.fast();
        PyType qualifierType = qualifier.getType(context);
        if (qualifierType == null || qualifierType instanceof PyTypeReference) {
          return true;
        }
      }
    }
    return false;
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
