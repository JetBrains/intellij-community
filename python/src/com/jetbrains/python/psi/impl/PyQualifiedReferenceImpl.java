package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.AssignmentCollectProcessor;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author yole
 */
public class PyQualifiedReferenceImpl extends PyReferenceImpl {
  public PyQualifiedReferenceImpl(PyQualifiedExpression element) {
    super(element);
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
    PyType qualifierType = qualifier.getType();
    if (qualifierType != null) {
      if (qualifier instanceof PyQualifiedExpression) {
        // enrich the type info with any fields assigned nearby
        List<PyQualifiedExpression> qualifier_path = PyResolveUtil.unwindQualifiers((PyQualifiedExpression)qualifier);
        if (qualifier_path != null) {
          for (PyExpression ex : collectAssignedAttributes((PyQualifiedExpression)qualifier)) {
            if (referencedName.equals(ex.getName())) {
              ret.poke(ex, RatedResolveResult.RATE_NORMAL);
              return ret;
            }
          }
        }
      }
      // resolve within the type proper
      PsiElement ref_elt = PyUtil.turnDirIntoInit(qualifierType.resolveMember(referencedName));
      if (ref_elt != null) ret.poke(ref_elt, RatedResolveResult.RATE_NORMAL);
    }
    // special case of __doc__
    if ("__doc__".equals(referencedName)) {
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
        PsiElement qual_object = ((PyReferenceExpression)qualifier).getReference().resolve();
        if (qual_object instanceof PyDocStringOwner) {
          docstring = ((PyDocStringOwner)qual_object).getDocStringExpression();
        }
      }
      if (docstring != null) {
        ret.poke(docstring, RatedResolveResult.RATE_HIGH);
      }
    }
    return ret;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    PyExpression qualifier = myElement.getQualifier();
    assert qualifier != null;

    PyType qualifierType = qualifier.getType();
    ProcessingContext ctx = new ProcessingContext();
    final Set<String> names_already = new HashSet<String>();
    ctx.put(PyType.CTX_NAMES, names_already);
    if (qualifierType != null) {
      Collection<Object> variants = new ArrayList<Object>();
      if (qualifier instanceof PyQualifiedExpression) {
        Collection<PyExpression> attrs = collectAssignedAttributes((PyQualifiedExpression)qualifier);
        variants.addAll(attrs);
        for (PyExpression ex : attrs) {
          if (ex instanceof PyReferenceExpression) {
            PyReferenceExpression refex = (PyReferenceExpression)ex;
            names_already.add(refex.getReferencedName());
          }
          else if (ex instanceof PyTargetExpression) {
            PyTargetExpression targetExpr = (PyTargetExpression) ex;
            names_already.add(targetExpr.getName());
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

}
