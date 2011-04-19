package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.console.completion.PydevConsoleReference;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.refactoring.PyDefUseUtil;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Implements reference expression PSI.
 *
 * @author yole
 */
public class PyReferenceExpressionImpl extends PyElementImpl implements PyReferenceExpression {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.psi.impl.PyReferenceExpressionImpl");

  public PyReferenceExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  @NotNull
  public PsiPolyVariantReference getReference() {
    return getReference(PyResolveContext.defaultContext());
  }

  @NotNull
  public PsiPolyVariantReference getReference(PyResolveContext context) {
    final PsiFile file = getContainingFile();
    final PyExpression qualifier = getQualifier();

    // Handle import reference
    if (PsiTreeUtil.getParentOfType(this, PyImportElement.class, PyFromImportStatement.class) != null) {
      return new PyImportReferenceImpl(this, context);
    }

    if (file != null) {
      // Return special reference
      final ConsoleCommunication communication = file.getCopyableUserData(PydevConsoleRunner.CONSOLE_KEY);
      if (communication != null) {
        if (qualifier != null) {
          return new PydevConsoleReference(this, communication, qualifier.getText() + ".");
        }
        return new PydevConsoleReference(this, communication, "");
      }
    }

    if (qualifier != null) {
      return new PyQualifiedReferenceImpl(this, context);
    }

    return new PyReferenceImpl(this, context);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyReferenceExpression(this);
  }

  @Nullable
  public PyExpression getQualifier() {
    final ASTNode[] nodes = getNode().getChildren(PyElementTypes.EXPRESSIONS);
    return (PyExpression)(nodes.length == 1 ? nodes[0].getPsi() : null);
  }

  @Nullable
  public String getReferencedName() {
    final ASTNode nameElement = getNameElement();
    return nameElement != null ? nameElement.getText() : null;
  }

  @Nullable
  public ASTNode getNameElement() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @Nullable
  @Override
  public String getName() {
    return getReferencedName();
  }


  private final QualifiedResolveResult EMPTY_RESULT = new QualifiedResolveResultEmpty();

  @NotNull
  public QualifiedResolveResult followAssignmentsChain(TypeEvalContext context) {
    PyReferenceExpression seeker = this;
    QualifiedResolveResult ret = null;
    List<PyExpression> qualifiers = new ArrayList<PyExpression>();
    PyExpression qualifier = seeker.getQualifier();
    if (qualifier != null) {
      qualifiers.add(qualifier);
    }
    Set<PyExpression> visited = new HashSet<PyExpression>();
    visited.add(this);
    SEARCH:
    while (ret == null) {
      final PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(context);
      ResolveResult[] targets = seeker.getReference(resolveContext).multiResolve(false);
      for (ResolveResult target : targets) {
        PsiElement elt = target.getElement();
        if (elt instanceof PyTargetExpression) {
          if (!context.maySwitchToAST((PyTargetExpression)elt)) {
            break;
          }
          PyExpression assigned_from = ((PyTargetExpression)elt).findAssignedValue();
          if (assigned_from instanceof PyReferenceExpression) {
            if (visited.contains(assigned_from)) {
              break;
            }
            visited.add(assigned_from);
            seeker = (PyReferenceExpression)assigned_from;
            if (seeker.getQualifier() != null) {
              qualifiers.add(seeker.getQualifier());
            }
            continue SEARCH;
          }
          else if (assigned_from != null) ret = new QualifiedResolveResultImpl(assigned_from, qualifiers, false);
        }
        else if (ret == null && elt instanceof PyElement && target.isValidResult()) {
          // remember this result, but a further reference may be the next resolve result
          ret = new QualifiedResolveResultImpl(target.getElement(), qualifiers, target instanceof ImplicitResolveResult);
        }
      }
      // all resolve results checked, reassignment not detected, nothing more to do
      break;
    }
    if (ret == null) ret = EMPTY_RESULT;
    return ret;
  }

  @Nullable
  public PyQualifiedName asQualifiedName() {
    final List<PyReferenceExpression> components = PyResolveUtil.unwindQualifiers((PyReferenceExpression)this);
    if (components == null) {
      return null;
    }
    return PyQualifiedName.fromReferenceChain(components);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    // in statements, process only the section in which the original expression was located
    PsiElement parent = getParent();
    if (parent instanceof PyStatement && lastParent == null && PsiTreeUtil.isAncestor(parent, place, true)) {
      return true;
    }

    // never resolve to references within the same assignment statement
    if (getParent() instanceof PyAssignmentStatement) {
      PsiElement placeParent = place.getParent();
      while (placeParent != null && placeParent instanceof PyExpression) {
        placeParent = placeParent.getParent();
      }
      if (placeParent == getParent()) {
        return true;
      }
    }

    if (this == place) return true;
    return processor.execute(this, substitutor);
  }

  @Override
  public String toString() {
    return "PyReferenceExpression: " + getReferencedName();
  }

  public PyType getType(@NotNull TypeEvalContext context) {
    if (!TypeEvalStack.mayEvaluate(this)) {
      return null;
    }
    try {
      final PyExpression qualifier = getQualifier();
      if (qualifier == null) {
        String name = getReferencedName();
        if (PyNames.NONE.equals(name)) {
          return PyNoneType.INSTANCE;
        }
      }
      else {
        PyType maybe_type = PyUtil.getSpecialAttributeType(this, context);
        if (maybe_type != null) return maybe_type;
        Ref<PyType> typeOfProperty = getTypeOfProperty(context);
        if (typeOfProperty != null) {
          return typeOfProperty.get();
        }
      }
      PyType type = getTypeFromProviders(context);
      if (type != null) {
        return type;
      }

      ResolveResult[] targets = getReference(PyResolveContext.noImplicits().withTypeEvalContext(context)).multiResolve(false);
      if (targets.length == 0) return null;
      for (ResolveResult resolveResult : targets) {
        PsiElement target = resolveResult.getElement();
        if (target == this || target == null) {
          continue;
        }
        type = getTypeFromTarget(target, context, this);
        if (type != null) {
          return type;
        }
      }
      return null;
    }
    finally {
      TypeEvalStack.evaluated(this);
    }
  }

  @Nullable
  public Ref<PyType> getTypeOfProperty(@NotNull TypeEvalContext context) {
    PyExpression qualifier = getQualifier();
    final String name = getName();
    if (name != null && qualifier != null) {
      PyType qualifier_type = context.getType(qualifier);
      if (qualifier_type instanceof PyClassType) {
        PyClass pyClass = ((PyClassType)qualifier_type).getPyClass();
        if (pyClass != null) {
          Property property = pyClass.findProperty(name);
          if (property != null) {
            final Maybe<PyFunction> accessor = property.getByDirection(AccessDirection.of(this));
            if (!accessor.isDefined()) {
              return Ref.create(null);
            }
            PsiElement resolved = this.getReference().resolve(); // to a correct accessor
            if (resolved instanceof Callable) {
              PyType type = ((Callable)resolved).getReturnType(context, this);
              if (type != null) return Ref.create(type);
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private PyType getTypeFromProviders(TypeEvalContext context) {
    for (PyTypeProvider provider : Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
      try {
        final PyType type = provider.getReferenceExpressionType(this, context);
        if (type != null) {
          return type;
        }
      }
      catch (AbstractMethodError e) {
        LOG.info(e);
      }
    }
    return null;
  }

  @Nullable
  public static PyType getTypeFromTarget(@NotNull final PsiElement target,
                                         final TypeEvalContext context,
                                         @Nullable PyReferenceExpression anchor) {
    final PyType pyType = getReferenceTypeFromProviders(target, context, anchor);
    if (pyType != null) {
      return pyType;
    }
    if (target instanceof PyTargetExpression) {
      final String name = ((PyTargetExpression)target).getName();
      if (PyNames.NONE.equals(name)) {
        return PyNoneType.INSTANCE;
      }
      if (PyNames.TRUE.equals(name) || PyNames.FALSE.equals(name)) {
        return PyBuiltinCache.getInstance(target).getBoolType();
      }
    }
    if (target instanceof PyFile) {
      return new PyModuleType((PyFile)target);
    }
    if (target instanceof PyImportedModule) {
      return new PyImportedModuleType((PyImportedModule)target);
    }
    if ((target instanceof PyTargetExpression || target instanceof PyNamedParameter) && context.allowDataFlow() && anchor != null) {
      final ScopeOwner scopeOwner = PsiTreeUtil.getParentOfType(anchor, ScopeOwner.class);
      if (scopeOwner != null && scopeOwner == PsiTreeUtil.getParentOfType(target, ScopeOwner.class)) {
        PyAugAssignmentStatement augAssignment = PsiTreeUtil.getParentOfType(anchor, PyAugAssignmentStatement.class);
        try {
          final PyElement[] defs = PyDefUseUtil.getLatestDefs(scopeOwner, (PyElement)target,
                                                              augAssignment != null ? augAssignment : anchor);
          if (defs.length > 0) {
            PyType type = getTypeIfExpr(defs[0], context);
            for (int i = 1; i < defs.length; i++) {
              type = PyUnionType.union(type, getTypeIfExpr(defs[i], context));
            }
            return type;
          }
        }
        catch (PyDefUseUtil.InstructionNotFoundException e) {
          // ignore
        }
      }
    }
    if (target instanceof PyExpression) {
      return context.getType((PyExpression)target);
    }
    if (target instanceof PyClass) {
      return new PyClassType((PyClass)target, true);
    }
    if (target instanceof PsiDirectory) {
      PsiFile file = ((PsiDirectory)target).findFile(PyNames.INIT_DOT_PY);
      if (file != null) return getTypeFromTarget(file, context, anchor);
    }
    return null;
  }

  @Nullable
  private static PyType getTypeIfExpr(PyElement def, TypeEvalContext context) {
    return def instanceof PyExpression ? context.getType((PyExpression)def) : null;
  }

  @Nullable
  public static PyType getReferenceTypeFromProviders(@NotNull final PsiElement target,
                                                     TypeEvalContext context,
                                                     @Nullable PsiElement anchor) {
    for (PyTypeProvider provider : Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
      final PyType result = provider.getReferenceType(target, context, anchor);
      if (result != null) return result;
    }

    return null;
  }

  private static class QualifiedResolveResultImpl extends RatedResolveResult implements QualifiedResolveResult {
    // a trivial implementation
    private List<PyExpression> myQualifiers;
    private boolean myIsImplicit;

    public boolean isImplicit() {
      return myIsImplicit;
    }

    QualifiedResolveResultImpl(@NotNull PsiElement element, List<PyExpression> qualifiers, boolean isImplicit) {
      super(isImplicit ? RATE_LOW : RATE_NORMAL, element);
      myQualifiers = qualifiers;
      myIsImplicit = isImplicit;
    }

    @Override
    public List<PyExpression> getQualifiers() {
      return myQualifiers;
    }
  }

  private static class QualifiedResolveResultEmpty implements QualifiedResolveResult {
    // a trivial implementation

    public QualifiedResolveResultEmpty() {
    }

    @Override
    public List<PyExpression> getQualifiers() {
      return Collections.emptyList();
    }

    public PsiElement getElement() {
      return null;
    }

    public boolean isValidResult() {
      return false;
    }

    public boolean isImplicit() {
      return false;
    }
  }

}

