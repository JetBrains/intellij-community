package com.jetbrains.python.psi.impl;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.patterns.SyntaxMatchers;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;

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
    ResultList ret = new ResultList();

    final String referencedName = myElement.getReferencedName();
    if (referencedName == null) return ret;

    final PyExpression qualifier = myElement.getQualifier();
    assert qualifier != null;

    // regular attributes
    PyType qualifierType = qualifier.getType(TypeEvalContext.fast());
    // is it a class-private name qualified by a different class?
    if (PyUtil.isClassPrivateName(referencedName) && qualifierType instanceof PyClassType) {
      final List<? extends PsiElement> match = SyntaxMatchers.DEEP_IN_METHOD.search(qualifier);
      if (match == null || (match.size() > 1 && ((PyClassType)qualifierType).getPyClass() != match.get(match.size()-1))) {
        return Collections.emptyList();
      }
    }
    //
    if (qualifierType != null && !(qualifierType instanceof PyTypeReference)) {
      // resolve within the type proper
      AccessDirection ctx = AccessDirection.of(myElement);
      final List<? extends PsiElement> member_of_qualifier = qualifierType.resolveMember(referencedName, ctx);
      if (member_of_qualifier == null) {
        return ret; // qualifier is positive that such name cannot exist in it
      }
      for (PsiElement resolved : member_of_qualifier) {
        PsiElement ref_elt = PyUtil.turnDirIntoInit(resolved);
        if (ref_elt != null) ret.poke(ref_elt, RatedResolveResult.RATE_NORMAL);
        // NOTE: maybe rate non-first results lower.
      }
      // enrich the type info with any fields assigned nearby
      if (qualifier instanceof PyQualifiedExpression) {
        // enrich the type info with any fields assigned nearby
        if (addAssignedAttributes(ret, referencedName, qualifier)) return ret;
      }
      // resolve within the type proper
      addResolveMember(ret, referencedName, qualifierType, ctx);
    }
    else if (myContext.allowImplicits()) {
      final Collection<PyFunction> functions = PyFunctionNameIndex.find(referencedName, myElement.getProject());
      for (PyFunction function : functions) {
        if (function.getContainingClass() != null) {
          ret.add(new ImplicitResolveResult(function));
        }
      }
    }
    // special case of __doc__
    if ("__doc__".equals(referencedName)) {
      addDocReference(ret, qualifier, qualifierType);
    }
    return ret;
  }

  private static void addResolveMember(ResultList ret, String referencedName, PyType qualifierType, AccessDirection context) {
    final List<? extends PsiElement> members = qualifierType.resolveMember(referencedName, context);
    if (members != null) {
      int rate = RatedResolveResult.RATE_NORMAL;
      for (PsiElement member : members) {
        ret.poke(PyUtil.turnDirIntoInit(member), rate);
        rate = RatedResolveResult.RATE_LOW;
      }
    }
  }

  private static boolean addAssignedAttributes(ResultList ret, String referencedName, PyExpression qualifier) {
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

  private void addDocReference(ResultList ret, PyExpression qualifier, PyType qualifierType) {
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
        Collections.addAll(variants, qualifierType.getCompletionVariants(myElement, ctx));
        return variants.toArray();
      }
      else {
        return qualifierType.getCompletionVariants(myElement, ctx);
      }
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private static Collection<PyExpression> collectAssignedAttributes(PyQualifiedExpression qualifier) {
    List<PyQualifiedExpression> qualifier_path = PyResolveUtil.unwindQualifiers(qualifier);
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
    if (resolve() == element) {
      return true;
    }
    final String referencedName = myElement.getReferencedName();
    if (element instanceof PyFunction && Comparing.equal(referencedName, ((PyFunction)element).getName()) &&
        ((PyFunction)element).getContainingClass() != null) {
      final PyExpression qualifier = myElement.getQualifier();
      if (qualifier != null && qualifier.getType(TypeEvalContext.fast()) == null) {
        return true;
      }
    }
    return false;
  }
}
