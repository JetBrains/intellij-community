package com.jetbrains.python.psi.types;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.ProjectScope;
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
    "__repr__", // TODO __module__ actually belongs to object's metaclass, it's not available to instances
    "__doc__",
    "__class__",
    "__module__",
    "__dict__"
  );

  private static final Logger LOG = Logger.getInstance(PyTypeFromUsedAttributesHelper.class);

  private final TypeEvalContext myContext;
  private final Map<PyClass, Set<PyClass>> myAncestorsCache = Maps.newHashMap();

  /**
   * @see #getType(com.jetbrains.python.psi.PyExpression)
   */
  @Nullable
  public static PyType getType(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    return new PyTypeFromUsedAttributesHelper(context).getType(expression);
  }

  @VisibleForTesting
  public PyTypeFromUsedAttributesHelper(@NotNull TypeEvalContext context) {
    myContext = context;
  }

  /**
   * Attempts to guess the type of a given expression based on what attributes (including special names) were accessed on it. If several
   * types fit then their union is returned. Suggested classes are ordered according to their
   * {@link PyTypeFromUsedAttributesHelper.Priority}. Currently at most {@link #MAX_CANDIDATES} can be returned in a union.
   */
  @Nullable
  public PyType getType(@NotNull PyExpression expression) {
    if (!ENABLED || !myContext.allowLocalUsages(expression)) {
      return null;
    }
    final long startInference = System.currentTimeMillis();
    final Set<String> seenAttrs = collectUsedAttributes(expression);
    LOG.debug(String.format("Attempting to infer type for expression: %s. Used attributes: %s", expression.getText(), seenAttrs));
    final Set<PyClass> allCandidates = suggestCandidateClasses(expression, seenAttrs);

    final long startPrioritization = System.currentTimeMillis();
    final List<CandidateClass> bestCandidates = prepareCandidates(allCandidates, expression);
    LOG.debug("Total " + (System.currentTimeMillis() - startPrioritization) + " ms to prioritize candidate classes");
    LOG.debug("Total " + (System.currentTimeMillis() - startInference) + " ms to infer candidate classes");

    return PyUnionType.createWeakType(PyUnionType.union(ContainerUtil.map(bestCandidates, new Function<CandidateClass, PyType>() {
      @Override
      public PyType fun(CandidateClass cls) {
        return new PyClassTypeImpl(cls.myClass, false);
      }
    })));
  }

  @NotNull
  private Set<PyClass> suggestCandidateClasses(@NotNull final PyExpression expression, @NotNull Set<String> seenAttrs) {
    final Set<PyClass> candidates = Sets.newHashSet();
    for (String attribute : seenAttrs) {
      // Search for some of these attributes like __init__ may produce thousands of candidates in average SDK
      // and we probably don't want to confuse user with PyNames.FAKE_OLD_BASE anyway
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
    return Collections.unmodifiableSet(suitableClasses);
  }

  @NotNull
  private List<CandidateClass> prepareCandidates(@NotNull Set<PyClass> candidates, @NotNull final PyExpression expression) {
    final Set<QualifiedName> importQualifiers = collectImportQualifiers(expression.getContainingFile());

    final List<CandidateClass> prioritizedCandidates = ContainerUtil.map(candidates, new Function<PyClass, CandidateClass>() {
      @Override
      public CandidateClass fun(PyClass candidate) {
        return new CandidateClass(candidate, findPriority(candidate, expression, importQualifiers));
      }
    });
    Collections.sort(prioritizedCandidates);

    final List<CandidateClass> result = Lists.newArrayList();
    for (CandidateClass candidate : prioritizedCandidates) {
      if (result.size() == MAX_CANDIDATES || candidate.myPriority.compareTo(Priority.PROJECT) >= 0) {
        break;
      }
      result.add(candidate);
    }
    return Collections.unmodifiableList(result);
  }

  @NotNull
  private static Priority findPriority(@NotNull PyClass candidate, @NotNull PyExpression expression,
                                       @NotNull Set<QualifiedName> qualifiers) {
    final PsiFile candidateFile = candidate.getContainingFile();
    if (PyBuiltinCache.getInstance(expression).isBuiltin(candidate)) {
      return Priority.BUILTIN;
    }
    if (candidateFile == expression.getContainingFile()) {
      return Priority.SAME_FILE;
    }
    final String qualifiedName = candidate.getQualifiedName();
    if (qualifiedName != null) {
      for (QualifiedName qualifier : qualifiers) {
        if (QualifiedName.fromDottedString(qualifiedName).matchesPrefix(qualifier)) {
          return Priority.IMPORTED;
        }
      }
    }
    if (ProjectScope.getProjectScope(candidate.getProject()).contains(candidateFile.getVirtualFile())) {
      return Priority.PROJECT;
    }
    return Priority.OTHER;
  }

  @VisibleForTesting
  @NotNull
  public Set<QualifiedName> collectImportQualifiers(@NotNull PsiFile file) {
    final Set<QualifiedName> result = Sets.newHashSet();
    if (file instanceof PyFile) {
      final PyFile originalModule = (PyFile)file;
      for (PyFromImportStatement fromImport : originalModule.getFromImports()) {
        if (fromImport.isFromFuture()) {
          continue;
        }
        final PsiFileSystemItem importedModule = PyUtil.as(fromImport.resolveImportSource(), PsiFileSystemItem.class);
        if (importedModule == null) {
          continue;
        }
        final QualifiedName qName = findShortestImportableQName(file.getFirstChild(), importedModule.getVirtualFile());
        if (qName == null) {
          continue;
        }
        final PyImportElement[] importElements = fromImport.getImportElements();
        if (fromImport.isStarImport() || importElements.length == 0) {
          result.add(qName);
        }
        else {
          result.addAll(ContainerUtil.map(importElements, new Function<PyImportElement, QualifiedName>() {
            @Override
            public QualifiedName fun(PyImportElement element) {
              final QualifiedName name = element.getImportedQName();
              return name != null ? qName.append(name) : qName;
            }
          }));
        }
      }
      for (PyImportElement normalImport : originalModule.getImportTargets()) {
        ContainerUtil.addIfNotNull(result, normalImport.getImportedQName());
      }
    }
    return Collections.unmodifiableSet(result);
  }

  @NotNull
  private Set<String> getAllInheritedAttributeNames(@NotNull PyClass candidate) {
    final Set<String> availableAttrs = Sets.newHashSet(getAllDeclaredAttributeNames(candidate));
    for (PyClass parent : getAncestorClassesFast(candidate)) {
      availableAttrs.addAll(getAllDeclaredAttributeNames(parent));
    }
    return availableAttrs;
  }

  @VisibleForTesting
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
   * Returns all attributes: methods, class and instance fields that are declared directly in the specified class
   * (not taking inheritance into account).
   * <p/>
   * This method <b>must not</b> access the AST because it is being called during stub indexing.
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

  private static class CandidateClass implements Comparable<CandidateClass> {
    final PyClass myClass;
    final Priority myPriority;

    public CandidateClass(@NotNull PyClass cls, @NotNull Priority priority) {
      myClass = cls;
      myPriority = priority;
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
    // TODO How to implement it?
    DEPENDENCY,
    OTHER
  }
}
