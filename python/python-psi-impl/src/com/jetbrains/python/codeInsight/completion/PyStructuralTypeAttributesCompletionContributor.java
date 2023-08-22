package com.jetbrains.python.codeInsight.completion;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.stubs.PyClassAttributesIndex;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.jetbrains.python.psi.PyUtil.as;

/**
 * This completion contributor tries to map structural type (if any) of qualifier under caret to set of concrete
 * classes (nominal types) that have (declare and/or inherit) all necessary attributes and thus complete other
 * attributes that may be accessed on this expression.
 * <p/>
 * Because it's somewhat computationally heavy operation that requires extensive resolution of superclasses and their
 * attributes, this contributor is activated only on smart completion.
 *
 * @author Mikhail Golubev
 */
public class PyStructuralTypeAttributesCompletionContributor extends CompletionContributor implements DumbAware {

  private static final Logger LOG = Logger.getInstance(PyStructuralTypeAttributesCompletionContributor.class);

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
  private static final PsiElementPattern.Capture<PsiElement> ATTRIBUTE_PATTERN =
    psiElement(PyTokenTypes.IDENTIFIER).afterLeaf(psiElement(PyTokenTypes.DOT)).withParent(psiElement(PyReferenceExpression.class));

  public PyStructuralTypeAttributesCompletionContributor() {
    extend(CompletionType.SMART, ATTRIBUTE_PATTERN, new AttributesCompletionProvider());
  }

  private static class AttributesCompletionProvider extends CompletionProvider<CompletionParameters> {
    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {

      final PsiElement position = parameters.getPosition();
      final PyReferenceExpression refExpr = as(position.getParent(), PyReferenceExpression.class);
      if (refExpr == null || !refExpr.isQualified() || parameters.getCompletionType() != CompletionType.SMART) {
        return;
      }

      final TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(refExpr.getProject(), parameters.getOriginalFile());
      //noinspection ConstantConditions
      final PyStructuralType structType = as(typeEvalContext.getType(refExpr.getQualifier()), PyStructuralType.class);
      LOG.debug("Structural type: " + structType);
      if (structType != null) {
        final Set<String> names = Sets.newHashSet(structType.getAttributeNames());
        // Remove "dummy" identifier from the set of attributes
        names.remove(refExpr.getReferencedName());
        final Set<PyClass> suitableClasses = suggestClassesFromUsedAttributes(refExpr, names, typeEvalContext);
        LOG.debug("Result classes that contain attributes " + names + ": ", suitableClasses);
        for (PyClass pyClass : suitableClasses) {
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

    private Set<PyClass> suggestClassesFromUsedAttributes(@NotNull PsiElement anchor,
                                                          @NotNull Set<String> seenAttrs,
                                                          @NotNull TypeEvalContext context) {
      final Set<PyClass> candidates = new HashSet<>();
      final Map<PyClass, Set<PyClass>> ancestorsCache = Maps.newHashMap();
      for (String attribute : seenAttrs) {
        // Search for some of these attributes like __init__ may produce thousands of candidates in average SDK
        if (COMMON_OBJECT_ATTRIBUTES.contains(attribute)) {
          candidates.add(PyBuiltinCache.getInstance(anchor).getClass(PyNames.OBJECT));
        }
        else {
          final Collection<PyClass> declaringClasses = PyClassAttributesIndex.find(attribute, anchor.getProject());
          if (LOG.isDebugEnabled()) {
            LOG.debug("Classes containing " + attribute + ": " +
                      ContainerUtil.map(declaringClasses, AttributesCompletionProvider::debugClassCoordinates));
          }
          candidates.addAll(declaringClasses);
        }
      }

      final Set<PyClass> suitableClasses = new HashSet<>();
      for (PyClass candidate : candidates) {
        if (PyUserSkeletonsUtil.isUnderUserSkeletonsDirectory(candidate.getContainingFile())) {
          continue;
        }
        final Set<String> inherited = getAllInheritedAttributeNames(candidate, context, ancestorsCache);
        if (LOG.isDebugEnabled()) {
          LOG.debug("All attributes of " + debugClassCoordinates(candidate) + ": " + inherited);
        }
        if (inherited.containsAll(seenAttrs)) {
          suitableClasses.add(candidate);
        }
      }
      return Collections.unmodifiableSet(suitableClasses);
    }

    @NotNull
    private Set<String> getAllInheritedAttributeNames(@NotNull PyClass candidate,
                                                      @NotNull TypeEvalContext context,
                                                      @NotNull Map<PyClass, Set<PyClass>> ancestorsCache) {
      final Set<String> availableAttrs = Sets.newHashSet(PyClassAttributesIndex.getAllDeclaredAttributeNames(candidate));
      for (PyClass parent : getAncestorClassesFast(candidate, context, ancestorsCache)) {
        availableAttrs.addAll(PyClassAttributesIndex.getAllDeclaredAttributeNames(parent));
      }
      return availableAttrs;
    }

    /**
     * A simpler and faster alternative to {@link PyClass#getAncestorClasses(TypeEvalContext)}.
     * The approach used here does not require proper MRO order of ancestors and its performance is greatly improved by reusing
     * intermediate results in case of a large class hierarchy.
     */
    @NotNull
    private Set<PyClass> getAncestorClassesFast(@NotNull PyClass pyClass,
                                                @NotNull TypeEvalContext context,
                                                @NotNull Map<PyClass, Set<PyClass>> ancestorsCache) {
      final Set<PyClass> ancestors = ancestorsCache.get(pyClass);
      if (ancestors != null) {
        return ancestors;
      }
      // Sentinel value to prevent infinite recursion
      ancestorsCache.put(pyClass, Collections.emptySet());
      final Set<PyClass> result = new HashSet<>();
      try {
        for (final PyClassLikeType baseType : pyClass.getSuperClassTypes(context)) {
          if (!(baseType instanceof PyClassType)) {
            continue;
          }
          final PyClass baseClass = ((PyClassType)baseType).getPyClass();
          result.add(baseClass);
          result.addAll(getAncestorClassesFast(baseClass, context, ancestorsCache));
        }
      }
      finally {
        // May happen in case of cyclic inheritance
        result.remove(pyClass);
        ancestorsCache.put(pyClass, Collections.unmodifiableSet(result));
      }
      return result;
    }

    @NotNull
    private static String debugClassCoordinates(PyClass cls) {
      return cls.getQualifiedName() + " (" + cls.getContainingFile().getVirtualFile().getPath() + ")";
    }
  }
}
