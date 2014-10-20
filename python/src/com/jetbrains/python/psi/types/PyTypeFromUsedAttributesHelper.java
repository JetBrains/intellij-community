package com.jetbrains.python.psi.types;

import com.google.common.collect.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.stubs.PyClassAttributesIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.resolve.QualifiedNameFinder.findShortestImportableQName;

/**
 * @author Mikhail Golubev
 */
public class PyTypeFromUsedAttributesHelper {
  public static final int MAX_CANDIDATES = 5;
  // Not final so it can be changed in debugger
  private static boolean ENABLED = Boolean.parseBoolean(System.getProperty("py.infer.types.from.used.attributes", "true"));

  private static final Set<String> COMMON_OBJECT_ATTRIBUTES = ImmutableSet.of(
    "__init__",
    "__new__",
    "__str__",
    "__repr__", // TODO __module__ actually belongs to object's metaclass, it's not available to intances
    "__doc__",
    "__class__",
    "__module__",
    "__dict__"
  );

  private static final Logger LOG = Logger.getInstance(PyTypeFromUsedAttributesHelper.class);

  private final TypeEvalContext myContext;
  private final Map<PyClass, Set<PyClass>> myAncestorsCache = Maps.newHashMap();

  public PyTypeFromUsedAttributesHelper(@NotNull TypeEvalContext context) {
    myContext = context;
  }

  /**
   * Attempts to guess type of given expression based on what attributes (including special names) were accessed on it. If several types
   * fit their union will be returned. Suggested classes will be ordered according to their {@link PyTypeFromUsedAttributesHelper.Priority}.
   * Currently at most {@link #MAX_CANDIDATES} can be returned in union.
   *
   * @return described type or {@code null} if nothing suits
   */
  @Nullable
  public PyType getType(@NotNull PyExpression expression) {
    if (!ENABLED || !myContext.allowLocalUsages(expression)) {
      return null;
    }
    final long startTime = System.currentTimeMillis();
    final Set<String> seenAttrs = collectUsedAttributes(expression);
    LOG.debug(String.format("Attempting to infer type for expression: %s. Used attributes: %s", expression.getText(), seenAttrs));
    final List<CandidateClass> allCandidates = suggestCandidateClasses(expression, seenAttrs);
    LOG.debug("Total " + (System.currentTimeMillis() - startTime) + " ms to infer candidate classes");

    final List<CandidateClass> bestCandidates = allCandidates.subList(0, Math.min(allCandidates.size(), MAX_CANDIDATES));
    return PyUnionType.createWeakType(PyUnionType.union(ContainerUtil.map(bestCandidates, new Function<CandidateClass, PyType>() {
      @Override
      public PyType fun(CandidateClass cls) {
        return new PyClassTypeImpl(cls.myClass, false);
      }
    })));
  }

  @NotNull
  private List<CandidateClass> suggestCandidateClasses(@NotNull final PyExpression expression, @NotNull Set<String> seenAttrs) {
    final Set<PyClass> candidates = Sets.newHashSet();
    for (String attribute : seenAttrs) {
      // Search for some of these attributes like __init__ may produce thousands of candidates in average SDK
      // and we probably don't want to confuse user with __Classobj anyway
      if (COMMON_OBJECT_ATTRIBUTES.contains(attribute)) {
        candidates.add(PyBuiltinCache.getInstance(expression).getClass(PyNames.OBJECT));
      }
      else {
        final Collection<PyClass> declaringClasses = PyClassAttributesIndex.find(attribute, expression.getProject());
        LOG.debug("Attribute " + attribute + " is declared in " + declaringClasses.size() + " classes");
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

    for (PyClass candidate : Lists.newArrayList(suitableClasses)) {
      for (PyClass ancestor : getAncestorClassesFast(candidate)) {
        if (suitableClasses.contains(ancestor)) {
          suitableClasses.remove(candidate);
        }
      }
    }
    final List<CandidateClass> result = ContainerUtil.map(suitableClasses, new Function<PyClass, CandidateClass>() {
      @Override
      public CandidateClass fun(PyClass pyClass) {
        return new CandidateClass(pyClass, expression);
      }
    });
    Collections.sort(result);
    return result;
  }

  @NotNull
  private Set<String> getAllInheritedAttributeNames(@NotNull PyClass candidate) {
    final Set<String> availableAttrs = Sets.newHashSet(getAllDeclaredAttributeNames(candidate));
    for (PyClass parent : getAncestorClassesFast(candidate)) {
      availableAttrs.addAll(getAllDeclaredAttributeNames(parent));
    }
    return availableAttrs;
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

  /**
   * Returns all attributes: methods, class and instance fields that are declared directly in specified class
   * (not taking inheritance into account).
   * <p/>
   * This method must not access to AST because it's called during stub indexing process.
   */
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

  /**
   * Simpler and faster alternative to {@link com.jetbrains.python.psi.impl.PyClassImpl#getAncestorClasses()}.
   * Approach used here does not require proper MRO order of ancestors and performance is greatly improved by reusing
   * of intermediate results in case of large class hierarchy.
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

  private static class CandidateClass implements Comparable<CandidateClass> {
    final PyClass myClass;
    final Priority myPriority;
    private final PyExpression myAnchor;

    public CandidateClass(@NotNull PyClass cls, @NotNull PyExpression anchor) {
      myClass = cls;
      myAnchor = anchor;
      myPriority = findPriority();
    }

    @NotNull
    private Priority findPriority() {
      if (PyBuiltinCache.getInstance(myClass).isBuiltin(myClass)) {
        return Priority.BUILTIN;
      }
      final PsiFile originalFile = myAnchor.getContainingFile();
      final PsiFile candidateFile = myClass.getContainingFile();
      if (candidateFile == originalFile) {
        return Priority.SAME_FILE;
      }
      if (originalFile instanceof PyFile) {
        final PyFile anchorModule = (PyFile)originalFile;

        final QualifiedName moduleQName = findShortestImportableQName(myAnchor, candidateFile.getVirtualFile());
        for (PyFromImportStatement fromImportStatement : anchorModule.getFromImports()) {
          if (Comparing.equal(fromImportStatement.getImportSourceQName(), moduleQName)) {
            if (fromImportStatement.isStarImport()) {
              return Priority.IMPORTED;
            }
            for (PyImportElement importElement : fromImportStatement.getImportElements()) {
              final PyReferenceExpression expression = importElement.getImportReferenceExpression();
              if (expression != null && Comparing.equal(expression.getName(), myClass.getName())) {
                return Priority.IMPORTED;
              }
            }
          }
        }
        for (PyImportElement importElement : anchorModule.getImportTargets()) {
          if (Comparing.equal(importElement.getImportedQName(), moduleQName)) {
            return Priority.IMPORTED;
          }
        }
      }
      if (ProjectRootManager.getInstance(myAnchor.getProject()).getFileIndex().isInSource(candidateFile.getVirtualFile())) {
        return Priority.PROJECT;
      }
      return Priority.OTHER;
    }

    @Override
    public int compareTo(@NotNull CandidateClass o) {
      // Alphabetical tie-breaker for consistent results
      //noinspection ConstantConditions
      return ComparisonChain.start()
        .compare(myPriority, o.myPriority)
        .compare(myClass.getName(), o.myClass.getName())
        .result();
    }

    @Override
    public String toString() {
      return String.format("ClassCandidate(name='%s' priority=%s)", myClass.getName(), myPriority);
    }
  }

  enum Priority {
    BUILTIN,
    SAME_FILE,
    IMPORTED,
    PROJECT,
    // TODO How implement?
    DEPENDENCY,
    OTHER
  }
}
