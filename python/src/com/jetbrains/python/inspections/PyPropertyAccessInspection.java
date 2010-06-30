package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Checks that properties are accessed correctly.
 * User: dcheryasov
 * Date: Jun 29, 2010 5:55:52 AM
 */
public class PyPropertyAccessInspection extends PyInspection {

  ThreadLocal<HashMap<Pair<PyClass, String>, Property>> myPropertyCache;

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.property.access");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public void inspectionStarted(LocalInspectionToolSession session) {
    super.inspectionStarted(session);
    myPropertyCache = new ThreadLocal<HashMap<Pair<PyClass, String>, Property>>();
    myPropertyCache.set(new HashMap<Pair<PyClass, String>, Property>());
  }

  @Override
  public void inspectionFinished(LocalInspectionToolSession session) {
    myPropertyCache.set(null); // help gc
    myPropertyCache = null;
    super.inspectionFinished(session);
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  public class Visitor extends PyInspectionVisitor {

    public Visitor(@Nullable final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyReferenceExpression(PyReferenceExpression node) {
      super.visitPyReferenceExpression(node);
      PyExpression qualifier = node.getQualifier();
      if (qualifier != null) {
        PyType type = qualifier.getType(TypeEvalContext.fast());
        if (type instanceof PyClassType) {
          PyClass cls = ((PyClassType)type).getPyClass();
          String name = node.getName();
          if (cls != null && name != null) {
            Map<Pair<PyClass, String>, Property> cache = PyPropertyAccessInspection.this.myPropertyCache.get();
            final Pair<PyClass, String> key = new Pair<PyClass, String>(cls, name);
            Property property;
            if (cache.containsKey(key)) property = cache.get(key);
            else property = cls.findProperty(name);
            cache.put(key, property); // we store nulls, too, to know that a property does not exist
            if (property != null) {
              AccessDirection dir = AccessDirection.of(node);
              checkAccessor(node, name, dir, property);
              if (dir == AccessDirection.READ && node.getParent() instanceof PyAugAssignmentStatement) {
                checkAccessor(node, name, AccessDirection.WRITE, property);
              }
            }
          }
        }
      }
    }

    private void checkAccessor(PyReferenceExpression node, String name, AccessDirection dir, Property property) {
      Maybe<PyFunction> accessor = property.getByDirection(dir);
      if (accessor.isDefined() && accessor.value() == null) {
        String message;
        if (dir == AccessDirection.WRITE) message = PyBundle.message("INSP.property.$0.cant.be.set", name);
        else if (dir == AccessDirection.DELETE) message = PyBundle.message("INSP.property.$0.cant.be.deleted", name);
        else message = PyBundle.message("INSP.property.$0.cant.be.read", name);
        registerProblem(node, message);
      }
    }

  }
}
