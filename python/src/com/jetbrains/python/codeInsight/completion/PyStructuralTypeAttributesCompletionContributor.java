package com.jetbrains.python.codeInsight.completion;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.stubs.PyClassAttributesIndex;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.jetbrains.python.psi.PyUtil.as;
import static com.jetbrains.python.psi.types.PyTypeFromUsedAttributesHelper.getAllDeclaredAttributeNames;

/**
 * @author Mikhail Golubev
 */
public class PyStructuralTypeAttributesCompletionContributor extends CompletionContributor {

  private static final Set<String> COMMON_OBJECT_ATTRIBUTES = ImmutableSet.of(
    "__init__",
    "__new__",
    "__str__",
    "__repr__", // TODO __module__ actually belongs to object's metaclass, it's not available to instances
    "__doc__",
    "__class__",
    "__module__",
    "__dict__"
  );
  public static final PsiElementPattern.Capture<PsiElement> ATTRIBUTE_PATTERN =
    psiElement(PyTokenTypes.IDENTIFIER).afterLeaf(psiElement(PyTokenTypes.DOT)).withParent(psiElement(PyReferenceExpression.class));

  public PyStructuralTypeAttributesCompletionContributor() {
    extend(CompletionType.SMART, ATTRIBUTE_PATTERN, new AttributesCompletionProvider());
  }

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    super.fillCompletionVariants(parameters, result);
  }

  private static class AttributesCompletionProvider extends CompletionProvider<CompletionParameters> {
    private TypeEvalContext myContext;
    private Map<PyClass, Set<PyClass>> myAncestorsCache;

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  ProcessingContext context,
                                  @NotNull CompletionResultSet result) {

      final PsiElement position = parameters.getPosition();
      final PyReferenceExpression refExpr = as(position.getParent(), PyReferenceExpression.class);
      if (refExpr == null || !refExpr.isQualified() || parameters.getCompletionType() != CompletionType.SMART) {
        return;
      }

      myContext = TypeEvalContext.codeCompletion(refExpr.getProject(), parameters.getOriginalFile());
      myAncestorsCache = Maps.newHashMap();

      //noinspection ConstantConditions
      final PyStructuralType structType = as(myContext.getType(refExpr.getQualifier()), PyStructuralType.class);
      if (structType != null) {
        final Set<String> names = Sets.newHashSet(structType.getAttributeNames());
        // Remove "dummy" identifier from the set of attributes
        names.remove(refExpr.getReferencedName());
        for (PyClass pyClass : suggestClassesFromUsedAttributes(refExpr, names)) {
          final PsiElement origPosition = parameters.getOriginalPosition();
          final String prefix;
          if (origPosition != null && origPosition.getNode().getElementType() == PyTokenTypes.IDENTIFIER) {
            prefix = origPosition.getText();
          }
          else {
            prefix = "";
          }
          final Object[] variants = new PyClassTypeImpl(pyClass, false).getCompletionVariants(prefix, position, context);
          for (Object variant : variants) {
            if (variant instanceof LookupElement) {
              result.addElement((LookupElement)variant);
            }
          }
        }
      }
    }

    private Set<PyClass> suggestClassesFromUsedAttributes(@NotNull PyReferenceExpression expression, @NotNull Set<String> seenAttrs) {
      final Set<PyClass> candidates = Sets.newHashSet();
      for (String attribute : seenAttrs) {
        // Search for some of these attributes like __init__ may produce thousands of candidates in average SDK
        // and we probably don't want to confuse user with PyNames.FAKE_OLD_BASE anyway
        if (COMMON_OBJECT_ATTRIBUTES.contains(attribute)) {
          candidates.add(PyBuiltinCache.getInstance(expression).getClass(PyNames.OBJECT));
        }
        else {
          final Collection<PyClass> declaringClasses = PyClassAttributesIndex.find(attribute, expression.getProject());
          candidates.addAll(declaringClasses);
        }
      }

      final Set<PyClass> suitableClasses = Sets.newHashSet();
      for (PyClass candidate : candidates) {
        if (PyUserSkeletonsUtil.isUnderUserSkeletonsDirectory(candidate.getContainingFile())) {
          continue;
        }
        if (getAllInheritedAttributeNames(candidate).containsAll(seenAttrs)) {
          suitableClasses.add(candidate);
        }
      }
      return Collections.unmodifiableSet(suitableClasses);
    }

    @NotNull
    private Set<String> getAllInheritedAttributeNames(@NotNull PyClass candidate) {
      final Set<String> availableAttrs = Sets.newHashSet(getAllDeclaredAttributeNames(candidate));
      for (PyClass parent : getAncestorClassesFast(candidate)) {
        availableAttrs.addAll(getAllDeclaredAttributeNames(parent));
      }
      return availableAttrs;
    }

    /**
     * A simpler and faster alternative to {@link com.jetbrains.python.psi.impl.PyClassImpl#getAncestorClasses()}.
     * The approach used here does not require proper MRO order of ancestors and its performance is greatly improved by reusing
     * intermediate results in case of a large class hierarchy.
     */
    @NotNull
    private Set<PyClass> getAncestorClassesFast(@NotNull PyClass pyClass) {
      final Set<PyClass> ancestors = myAncestorsCache.get(pyClass);
      if (ancestors != null) {
        return ancestors;
      }
      // Sentinel value to prevent infinite recursion
      myAncestorsCache.put(pyClass, Collections.<PyClass>emptySet());
      final Set<PyClass> result = Sets.newHashSet();
      try {
        for (final PyClassLikeType baseType : pyClass.getSuperClassTypes(myContext)) {
          if (!(baseType instanceof PyClassType)) {
            continue;
          }
          final PyClass baseClass = ((PyClassType)baseType).getPyClass();
          result.add(baseClass);
          result.addAll(getAncestorClassesFast(baseClass));
        }
      }
      finally {
        // May happen in case of cyclic inheritance
        result.remove(pyClass);
        myAncestorsCache.put(pyClass, Collections.unmodifiableSet(result));
      }
      return result;
    }
  }
}
