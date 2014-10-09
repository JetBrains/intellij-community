package com.jetbrains.python.psi.types;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyClassAttributesIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Mikhail Golubev
 */
public class PyTypeInferenceFromUsedAttributesUtil {
  private PyTypeInferenceFromUsedAttributesUtil() {
    // empty
  }

  /**
   * Attempts to guess type of given expression based on what attributes (including special names) were accessed on it. If several types
   * fit their union will be returned.
   *
   * @param expr    expression which type should be inferred
   * @param context type evaluation context
   * @return described type or {@code null} if nothing suits
   */
  @Nullable
  public static PyType getTypeFromUsedAttributes(@NotNull PyExpression expr, @NotNull final TypeEvalContext context) {
    if (!context.maySwitchToAST(expr)) {
      return null;
    }
    final Set<String> seenAttrs;
    seenAttrs = collectUsedAttributes(expr);

    final Set<PyClass> candidates = Sets.newHashSet();
    for (String attribute : seenAttrs) {
      candidates.addAll(PyClassAttributesIndex.find(attribute, expr.getProject()));
    }

    final Set<PyClass> suitableClasses = Sets.newHashSet();
    for (PyClass candidate : candidates) {
      if (PyUserSkeletonsUtil.isUnderUserSkeletonsDirectory(candidate.getContainingFile())) {
        continue;
      }
      final Set<String> availableAttrs = Sets.newHashSet(getAllDeclaredAttributeNames(candidate));
      for (PyClass parent : candidate.getAncestorClasses(context)) {
        availableAttrs.addAll(getAllDeclaredAttributeNames(parent));
      }
      if (availableAttrs.containsAll(seenAttrs)) {
        suitableClasses.add(candidate);
      }
    }

    for (PyClass candidate : Lists.newArrayList(suitableClasses)) {
      for (PyClass ancestor : candidate.getAncestorClasses()) {
        if (suitableClasses.contains(ancestor)) {
          suitableClasses.remove(candidate);
        }
      }
    }
    // TODO: proper prioritisation of results
    final List<PyClass> orderedWinners = ContainerUtil.sorted(suitableClasses, new Comparator<PyClass>() {
      @Override
      public int compare(PyClass o1, PyClass o2) {
        return StringUtil.compare(o1.getName(), o2.getName(), true);
      }
    });
    return PyUnionType.union(ContainerUtil.map(orderedWinners, new Function<PyClass, PyType>() {
      @Override
      public PyType fun(PyClass cls) {
        return new PyClassTypeImpl(cls, false);
      }
    }));
  }

  @NotNull
  public static Set<String> collectUsedAttributes(@NotNull PyExpression element) {
    final QualifiedName qualifiedName;
    if (element instanceof PyQualifiedExpression) {
      qualifiedName = ((PyQualifiedExpression)element).asQualifiedName();
    }
    else {
      final String elementName = element.getName();
      qualifiedName = elementName == null ? null : QualifiedName.fromDottedString(elementName);
    }
    if (qualifiedName == null) {
      return Collections.emptySet();
    }
    return collectUsedAttributes(element, qualifiedName);
  }

  @NotNull
  private static Set<String> collectUsedAttributes(@NotNull PyExpression anchor, @NotNull final QualifiedName qualifiedName) {
    final Set<String> result = Sets.newHashSet();

    final PsiReference reference = anchor.getReference();
    final ScopeOwner definitionScope = ScopeUtil.getScopeOwner(reference != null ? reference.resolve() : anchor);
    ScopeOwner scope = ScopeUtil.getScopeOwner(anchor);
    while (scope != null) {
      final ScopeOwner inspectedScope = scope;
      scope.accept(new PyRecursiveElementVisitor() {
        @Override
        public void visitPyElement(PyElement node) {
          if (node instanceof ScopeOwner && node != inspectedScope) {
            return;
          }
          if (node instanceof PyQualifiedExpression) {
            ContainerUtil.addIfNotNull(result, getAttributeOfQualifier(((PyQualifiedExpression)node), qualifiedName));
          }
          super.visitPyElement(node);
        }
      });
      if (scope == definitionScope) {
        break;
      }
      scope = ScopeUtil.getScopeOwner(scope);
    }
    return result;
  }

  @Nullable
  private static String getAttributeOfQualifier(@NotNull PyQualifiedExpression expression, @NotNull QualifiedName expectedQualifier) {
    if (!expression.isQualified()) {
      return null;
    }
    final QualifiedName qualifiedName = expression.asQualifiedName();
    if (qualifiedName != null && qualifiedName.removeTail(1).equals(expectedQualifier)) {
      return qualifiedName.getLastComponent();
    }
    return null;
  }

  @NotNull
  public static List<String> getAllDeclaredAttributeNames(@NotNull PyClass pyClass) {
    final List<PsiNamedElement> members = ContainerUtil.<PsiNamedElement>concat(pyClass.getInstanceAttributes(),
                                                                                pyClass.getClassAttributes(),
                                                                                Arrays.asList(pyClass.getMethods(false)));

    return ContainerUtil.mapNotNull(members, new Function<PsiNamedElement, String>() {
      @Override
      public String fun(PsiNamedElement expression) {
        final String attrName = expression.getName();
        return attrName != null ? attrName : null;
      }
    });
  }
}
