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
      PyType.Context ctx;
      if (myElement instanceof PyTargetExpression) ctx = PyType.Context.WRITE;
      else if (myElement.getParent() instanceof PyDelStatement) ctx = PyType.Context.DELETE;
      else ctx = PyType.Context.READ;
      PsiElement ref_elt = PyUtil.turnDirIntoInit(qualifierType.resolveMember(referencedName, ctx));
      if (ref_elt != null) ret.poke(ref_elt, RatedResolveResult.RATE_NORMAL);
      // enrich the type info with any fields assigned nearby
      if (qualifier instanceof PyQualifiedExpression) {
        for (PyExpression ex : collectAssignedAttributes((PyQualifiedExpression)qualifier)) {
          if (referencedName.equals(ex.getName())) {
            ret.poke(ex, RatedResolveResult.RATE_NORMAL);
            return ret;
          }
        }
      }
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
      if (docstring != null) {
        ret.poke(docstring, RatedResolveResult.RATE_HIGH);
      }
    }
    /*
    // case of a field marked as @property_name.{getter,setter,deleter}
    final PsiFile containing_file = myElement.getContainingFile();
    if (containing_file != null) {
      final VirtualFile vfile = containing_file.getVirtualFile();
      if (vfile != null && LanguageLevel.forFile(vfile).isAtLeast(LanguageLevel.PYTHON26)) {
        String expected_accessor;
        if (PsiTreeUtil.getParentOfType(myElement, PyDelStatement.class) != null) expected_accessor = "deleter";
        else if (PsiTreeUtil.getParentOfType(myElement, PyTargetExpression.class) != null)  expected_accessor = "setter";
        else expected_accessor = "getter";

        final PsiElement parent = myElement.getParent();
        PyExpression prev_qualifier = null;
        if (qualifier instanceof PyQualifiedExpression) {
          prev_qualifier = ((PyQualifiedExpression)qualifier).getQualifier();
        }
        if (prev_qualifier == null && parent instanceof PyDecorator) {
          if (ArrayUtil.contains(referencedName, PROPERTY_DECO_ATTRIBUTES)) {
            // find above us a method named as referencedName and marked with @property
            PyFunction method = ((PyDecorator)parent).getTarget();
            if (method != null) {
              final PyClass containing_class = method.getContainingClass();
              if (containing_class != null) {
                for (PyFunction a_method : containing_class.getMethods()) { // not treeCrawlUp, use stubs
                  if (referencedName.equals(a_method.getName()) && "property".equals(PyUtil.getTheOnlyBuiltinDecorator(a_method))) {
                    // are we textually after that method?
                    if (a_method.getTextOffset() > method.getTextOffset()) {
                      ret.poke(a_method, RatedResolveResult.RATE_HIGH);
                      break;
                      // NOTE: maybe find the closest, since redefinitions are possible
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    */
    return ret;
  }

  protected final static String[] PROPERTY_DECO_ATTRIBUTES = {"getter", "setter", "deleter"};

  @NotNull
  @Override
  public Object[] getVariants() {
    PyExpression qualifier = myElement.getQualifier();
    assert qualifier != null;

    PyType qualifierType = qualifier.getType(TypeEvalContext.slow());
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
