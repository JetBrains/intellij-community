// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.unresolvedReference;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyCustomType;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonRuntimeService;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.codeInsight.PySubstitutionChunkReference;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.imports.OptimizeImportsQuickFix;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.documentation.docstrings.DocStringParameterReference;
import com.jetbrains.python.documentation.docstrings.DocStringTypeReference;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.inspections.PyInspectionVisitor;
import com.jetbrains.python.inspections.PyInspectionsUtil;
import com.jetbrains.python.inspections.quickfix.*;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyImportStatementNavigator;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.references.PyFromImportNameReference;
import com.jetbrains.python.psi.impl.references.PyImportReference;
import com.jetbrains.python.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.psi.impl.references.hasattr.PyHasAttrHelper;
import com.jetbrains.python.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.types.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.PyNames.END_WILDCARD;

public abstract class PyUnresolvedReferencesVisitor extends PyInspectionVisitor {
  private final Set<PyImportedNameDefiner> myAllImports = Collections.synchronizedSet(new HashSet<>());
  private final Set<PyImportedNameDefiner> myImportsInsideGuard = Collections.synchronizedSet(new HashSet<>());
  private final Set<PyImportedNameDefiner> myUsedImports = Collections.synchronizedSet(new HashSet<>());
  private final ImmutableSet<String> myIgnoredIdentifiers;
  private volatile Boolean myIsEnabled = null;

  public static final Key<PyInspection> INSPECTION = Key.create("PyUnresolvedReferencesVisitor.inspection");

  protected PyUnresolvedReferencesVisitor(@Nullable ProblemsHolder holder,
                                          @NotNull LocalInspectionToolSession session, List<String> ignoredIdentifiers) {
    super(holder, session);
    myIgnoredIdentifiers = ImmutableSet.copyOf(ignoredIdentifiers);
  }

  @Override
  public void visitPyTargetExpression(@NotNull PyTargetExpression node) {
    checkSlotsAndProperties(node);
  }

  private void checkSlotsAndProperties(PyQualifiedExpression node) {
    final PyExpression qualifier = node.getQualifier();
    final String attrName = node.getReferencedName();
    if (qualifier != null && attrName != null) {
      final PyType type = myTypeEvalContext.getType(qualifier);
      if (type instanceof PyClassType && !((PyClassType)type).isAttributeWritable(attrName, myTypeEvalContext)) {
        final ASTNode nameNode = node.getNameElement();
        final PsiElement e = nameNode != null ? nameNode.getPsi() : node;
        registerProblem(e, PyPsiBundle.message("INSP.unresolved.refs.class.object.has.no.attribute", type.getName(), attrName));
      }
    }
  }

  @Override
  public void visitPyImportElement(@NotNull PyImportElement node) {
    super.visitPyImportElement(node);
    final PyFromImportStatement fromImport = PsiTreeUtil.getParentOfType(node, PyFromImportStatement.class);
    if (isEnabled(node) && (fromImport == null || !fromImport.isFromFuture())) {
      myAllImports.add(node);
    }
  }

  @Override
  public void visitPyStarImportElement(@NotNull PyStarImportElement node) {
    super.visitPyStarImportElement(node);
    if (isEnabled(node)) {
      myAllImports.add(node);
    }
  }

  @Override
  public void visitComment(@NotNull PsiComment comment) {
    super.visitComment(comment);
    if (comment instanceof PsiLanguageInjectionHost) {
      processInjection((PsiLanguageInjectionHost)comment);
    }
  }

  @Override
  public void visitPyElement(final @NotNull PyElement node) {
    super.visitPyElement(node);
    final PsiFile file = node.getContainingFile();
    final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(node.getProject());
    if (injectedLanguageManager.isInjectedFragment(file)) {
      final PsiLanguageInjectionHost host = injectedLanguageManager.getInjectionHost(node);
      processInjection(host);
    }
    if (node instanceof PyReferenceOwner) {
      final PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(myTypeEvalContext);
      processReference(node, ((PyReferenceOwner)node).getReference(resolveContext));
    }
    else {
      if (node instanceof PsiLanguageInjectionHost) {
        processInjection((PsiLanguageInjectionHost)node);
      }
      for (final PsiReference reference : node.getReferences()) {
        processReference(node, reference);
      }
    }
  }

  private void processInjection(@Nullable PsiLanguageInjectionHost node) {
    if (node == null) return;
    final List<Pair<PsiElement, TextRange>> files = InjectedLanguageManager.getInstance(node.getProject()).getInjectedPsiFiles(node);
    if (files != null) {
      for (Pair<PsiElement, TextRange> pair : files) {
        new PyRecursiveElementVisitor() {
          @Override
          public void visitPyElement(@NotNull PyElement element) {
            super.visitPyElement(element);
            if (element instanceof PyReferenceOwner) {
              final PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(myTypeEvalContext);
              final PsiPolyVariantReference reference = ((PyReferenceOwner)element).getReference(resolveContext);
              markTargetImportsAsUsed(reference);
            }
          }
        }.visitElement(pair.getFirst());
      }
    }
  }

  private void markTargetImportsAsUsed(@NotNull PsiPolyVariantReference reference) {
    final ResolveResult[] resolveResults = reference.multiResolve(false);
    for (ResolveResult resolveResult : resolveResults) {
      if (resolveResult instanceof ImportedResolveResult) {
        final PyImportedNameDefiner definer = ((ImportedResolveResult)resolveResult).getDefiner();
        if (definer != null) {
          myUsedImports.add(definer);
        }
      }
    }
  }

  private void processReference(@NotNull PyElement node, @Nullable PsiReference reference) {
    if (!isEnabled(node) || reference == null || reference.isSoft()) {
      return;
    }
    final PyExceptPart guard = getImportErrorGuard(node);
    if (guard != null) {
      processReferenceInImportGuard(node, guard);
      return;
    }
    if (node instanceof PyQualifiedExpression) {
      final PyQualifiedExpression qExpr = (PyQualifiedExpression)node;
      final PyExpression qualifier = qExpr.getQualifier();
      final String name = node.getName();
      if (qualifier != null && name != null && PyHasAttrHelper.INSTANCE.getNamesFromHasAttrs(node, qualifier).contains(name)) {
        return;
      }
    }
    PsiElement target = null;
    final boolean unresolved;
    if (reference instanceof PsiPolyVariantReference) {
      final PsiPolyVariantReference poly = (PsiPolyVariantReference)reference;
      final ResolveResult[] resolveResults = poly.multiResolve(false);
      unresolved = (resolveResults.length == 0);
      for (ResolveResult resolveResult : resolveResults) {
        if (target == null && resolveResult.isValidResult()) {
          target = resolveResult.getElement();
        }
        if (resolveResult instanceof ImportedResolveResult) {
          final PyImportedNameDefiner definer = ((ImportedResolveResult)resolveResult).getDefiner();
          if (definer != null) {
            myUsedImports.add(definer);
          }
        }
      }
    }
    else {
      target = reference.resolve();
      unresolved = (target == null);
    }
    if (unresolved) {
      boolean ignoreUnresolved = ignoreUnresolved(node, reference);
      if (!ignoreUnresolved) {
        final HighlightSeverity severity = reference instanceof PsiReferenceEx
                                           ? ((PsiReferenceEx)reference).getUnresolvedHighlightSeverity(myTypeEvalContext)
                                           : HighlightSeverity.ERROR;
        if (severity == null) return;
        registerUnresolvedReferenceProblem(node, reference, severity);
      }
      // don't highlight unresolved imports as unused
      if (node.getParent() instanceof PyImportElement) {
        myAllImports.remove(node.getParent());
      }
    }
    else if (reference instanceof PyImportReference &&
             target == reference.getElement().getContainingFile() &&
             !isContainingFileImportAllowed(node, (PsiFile)target)) {
      registerProblem(node, PyPsiBundle.message("INSP.unresolved.refs.import.resolves.to.its.containing.file"));
    }
  }

  private void processReferenceInImportGuard(@NotNull PyElement node, @NotNull PyExceptPart guard) {
    final PyImportElement importElement = PsiTreeUtil.getParentOfType(node, PyImportElement.class);
    if (importElement != null) {
      final String visibleName = importElement.getVisibleName();
      final ScopeOwner owner = ScopeUtil.getScopeOwner(importElement);
      if (visibleName != null && owner != null) {
        final Collection<PsiElement> allWrites = ScopeUtil.getElementsOfAccessType(visibleName, owner, ReadWriteInstruction.ACCESS.WRITE);
        final boolean hasWriteInsideGuard = allWrites.stream().anyMatch(e -> PsiTreeUtil.isAncestor(guard, e, false));
        if (!hasWriteInsideGuard && !shouldSkipMissingWriteInsideGuard(guard, visibleName)) {
          myImportsInsideGuard.add(importElement);
        }
      }
    }
  }

  private void registerUnresolvedReferenceProblem(@NotNull PyElement node, @NotNull final PsiReference reference,
                                                @NotNull HighlightSeverity severity) {
    if (reference instanceof DocStringTypeReference) {
      return;
    }
    String description = null;
    PsiElement element = reference.getElement();
    final String text = element.getText();
    TextRange rangeInElement = reference.getRangeInElement();
    String refText = text;  // text of the part we're working with
    if (rangeInElement.getStartOffset() > 0 && rangeInElement.getEndOffset() > 0) {
      refText = rangeInElement.substring(text);
    }

    final String refName = (element instanceof PyQualifiedExpression) ? ((PyQualifiedExpression)element).getReferencedName() : refText;
    // Empty text, nothing to highlight
    if (refName == null || refName.length() <= 0) {
      return;
    }

    final List<QualifiedName> qualifiedNames = getCanonicalNames(reference, myTypeEvalContext);
    for (QualifiedName name : qualifiedNames) {
      final String canonicalName = name.toString();
      for (String ignored : myIgnoredIdentifiers) {
        if (ignored.endsWith(END_WILDCARD)) {
          final String prefix = ignored.substring(0, ignored.length() - END_WILDCARD.length());
          if (canonicalName.startsWith(prefix)) {
            return;
          }
        }
        else if (canonicalName.equals(ignored)) {
          return;
        }
      }
    }
    // Legacy non-qualified ignore patterns
    if (myIgnoredIdentifiers.contains(refName)) {
      return;
    }
    List<LocalQuickFix> fixes = new ArrayList<>();
    if (element instanceof PyReferenceExpression) {
      PyReferenceExpression expr = (PyReferenceExpression)element;
      if (PyNames.COMPARISON_OPERATORS.contains(refName)) {
        return;
      }
      if (!expr.isQualified()) {
        if (PyInspectionsUtil.hasAnyInterruptedControlFlowPaths(expr)) {
          return;
        }
        ContainerUtil.addIfNotNull(fixes, getTrueFalseQuickFix(expr, refText));
        ContainerUtil.addAll(fixes, getAddSelfFixes(myTypeEvalContext, node, expr));
        ContainerUtil.addIfNotNull(fixes, getCreateFunctionQuickFix(expr));
        ContainerUtil.addIfNotNull(fixes, getAddParameterQuickFix(refName, expr));
        fixes.add(new PyRenameUnresolvedRefQuickFix());
      }
      // unqualified:
      // may be module's
      if (PyModuleType.getPossibleInstanceMembers().contains(refName)) {
        return;
      }
      // may be a "try: import ..."; not an error not to resolve
      if ((
        PsiTreeUtil.getParentOfType(
          PsiTreeUtil.getParentOfType(node, PyImportElement.class), PyTryExceptStatement.class, PyIfStatement.class
        ) != null
      )) {
        severity = HighlightSeverity.WEAK_WARNING;
        description = PyPsiBundle.message("INSP.unresolved.refs.module.not.found", refText);
        // TODO: mark the node so that future references pointing to it won't result in a error, but in a warning
      }
    }
    if (reference instanceof PsiReferenceEx && description == null) {
      description = ((PsiReferenceEx)reference).getUnresolvedDescription();
    }
    if (description == null) {
      boolean markedQualified = false;
      if (element instanceof PyQualifiedExpression) {
        // TODO: Add __qualname__ for Python 3.3 to the skeleton of <class 'object'>, introduce a pseudo-class skeleton for
        // <class 'function'>
        if ("__qualname__".equals(refText) && !LanguageLevel.forElement(element).isPython2()) {
          return;
        }
        final PyQualifiedExpression expr = (PyQualifiedExpression)element;
        if (PyNames.COMPARISON_OPERATORS.contains(expr.getReferencedName())) {
          return;
        }
      }
      final PyExpression qualifier = getReferenceQualifier(reference);
      if (qualifier != null) {
        final PyType type = myTypeEvalContext.getType(qualifier);
        if (type != null) {
          if (ignoreUnresolvedMemberForType(type, reference, refName) || isDeclaredInSlots(type, refName)) {
            return;
          }
          ContainerUtil.addAll(fixes, getCreateMemberFromUsageFixes(type, reference, refText));
          if (type instanceof PyClassType) {
            final PyClassType classType = (PyClassType)type;
            if (reference instanceof PyOperatorReference) {
              String className = type.getName();
              if (classType.isDefinition()) {
                final PyClassLikeType metaClassType = classType.getMetaClassType(myTypeEvalContext, true);
                if (metaClassType != null) {
                  className = metaClassType.getName();
                }
              }
              description = PyPsiBundle.message("INSP.unresolved.refs.class.does.not.define.operator",
                                                className, refName,
                                                ((PyOperatorReference)reference).getReadableOperatorName());
            }
            else {
              description = PyPsiBundle.message("INSP.unresolved.refs.unresolved.attribute.for.class", refText, type.getName());
            }
          }
          else {
            description = PyPsiBundle.message("INSP.unresolved.refs.cannot.find.reference.in.type", refText, type.getName());
          }
          markedQualified = true;
        }
      }
      if (!markedQualified) {
        description = PyPsiBundle.message("INSP.unresolved.refs.unresolved.reference", refText);

        ContainerUtil.addAll(fixes, getAutoImportFixes(node, reference, element));
        ContainerUtil.addIfNotNull(fixes, getCreateClassFix(refText, element));
      }
    }
    ProblemHighlightType hl_type;
    if (severity == HighlightSeverity.WARNING) {
      hl_type = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
    }
    else if (severity == HighlightSeverity.ERROR) {
      hl_type = ProblemHighlightType.GENERIC_ERROR;
    }
    else {
      hl_type = ProblemHighlightType.LIKE_UNKNOWN_SYMBOL;
    }

    ContainerUtil.addAll(fixes, getImportStatementQuickFixes(element));
    ContainerUtil.addAll(fixes, getAddIgnoredIdentifierQuickFixes(qualifiedNames));
    ContainerUtil.addAll(fixes, getPluginQuickFixes(reference));
    ContainerUtil.addAll(fixes, getInstallPackageQuickFixes(node, reference, refName));

    if (reference instanceof PySubstitutionChunkReference) {
      return;
    }

    registerProblem(node, description, hl_type, null, rangeInElement, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
  }

  private boolean isDeclaredInSlots(@NotNull PyType type, @NotNull String attrName) {
    return PyTypeUtil.toStream(type)
                     .select(PyClassType.class)
                     .map(PyClassType::getPyClass)
                     .flatMap(cls -> StreamEx.of(cls).append(cls.getAncestorClasses(myTypeEvalContext)))
                     .nonNull()
                     .filter(c -> c.isNewStyleClass(myTypeEvalContext))
                     .flatCollection(PyClass::getOwnSlots)
                     .anyMatch(attrName::equals);
  }

  private boolean ignoreUnresolvedMemberForType(@NotNull PyType type, PsiReference reference, String name) {
    if (PyTypeChecker.isUnknown(type, myTypeEvalContext)) {
      // this almost always means that we don't know the type, so don't show an error in this case
      return true;
    }
    if (type instanceof PyStructuralType && ((PyStructuralType)type).isInferredFromUsages()) {
      return true;
    }
    if (type instanceof PyImportedModuleType) {
      PyImportedModule module = ((PyImportedModuleType)type).getImportedModule();
      if (module.resolve() == null) {
        return true;
      }
    }
    if (type instanceof PyCustomType) {
      // Skip custom member types that mimics another class with fuzzy parents
      for (final PyClassLikeType mimic : ((PyCustomType)type).getTypesToMimic()) {
        if (!(mimic instanceof PyClassType)) {
          continue;
        }
        if (PyUtil.hasUnresolvedAncestors(((PyClassType)mimic).getPyClass(), myTypeEvalContext)) {
          return true;
        }
      }
    }
    if (type instanceof PyClassType) {
      PyClass cls = ((PyClassType)type).getPyClass();
      if (PyTypeChecker.overridesGetAttr(cls, myTypeEvalContext)) {
        return true;
      }
      if (cls.findProperty(name, true, myTypeEvalContext) != null) {
        return true;
      }
      if (PyUtil.hasUnresolvedAncestors(cls, myTypeEvalContext)) {
        return true;
      }
      if (isDecoratedAsDynamic(cls, true)) {
        return true;
      }
      if (hasUnresolvedDynamicMember((PyClassType)type, reference, name, myTypeEvalContext)) return true;

      if (isAwaitOnGeneratorBasedCoroutine(name, reference, cls)) return true;
    }
    if (type instanceof PyFunctionTypeImpl) {
      final PyCallable callable = ((PyFunctionTypeImpl)type).getCallable();
      if (callable instanceof PyFunction &&
          PyKnownDecoratorUtil.hasUnknownOrUpdatingAttributesDecorator((PyFunction)callable, myTypeEvalContext)) {
        return true;
      }
    }
    if (type instanceof PyUnionType) {
      return ContainerUtil.exists(((PyUnionType)type).getMembers(), member -> ignoreUnresolvedMemberForType(member, reference, name));
    }
    if (type instanceof PyModuleType) {
      final PyFile module = ((PyModuleType)type).getModule();
      if (module.getLanguageLevel().isAtLeast(LanguageLevel.PYTHON37)) {
        return PyTypeChecker.definesGetAttr(module, myTypeEvalContext);
      }
    }
    for (PyInspectionExtension extension : PyInspectionExtension.EP_NAME.getExtensionList()) {
      if (extension.ignoreUnresolvedMember(type, name, myTypeEvalContext)) {
        return true;
      }
    }
    return false;
  }

  private boolean isDecoratedAsDynamic(@NotNull PyClass cls, boolean inherited) {
    if (inherited) {
      if (isDecoratedAsDynamic(cls, false)) {
        return true;
      }
      for (PyClass base : cls.getAncestorClasses(myTypeEvalContext)) {
        if (base != null && isDecoratedAsDynamic(base, false)) {
          return true;
        }
      }
    }
    else {
      if (PyKnownDecoratorUtil.hasUnknownDecorator(cls, myTypeEvalContext)) {
        return true;
      }
      final String docString = cls.getDocStringValue();
      if (docString != null && docString.contains("@DynamicAttrs")) {
        return true;
      }
    }
    return false;
  }

  private boolean isAwaitOnGeneratorBasedCoroutine(@NotNull String name, @NotNull PsiReference reference, @NotNull PyClass cls) {
    if (PyNames.DUNDER_AWAIT.equals(name) &&
        reference instanceof PyOperatorReference &&
        PyTypingTypeProvider.GENERATOR.equals(cls.getQualifiedName())) {
      final PyExpression receiver = ((PyOperatorReference)reference).getReceiver();

      if (receiver instanceof PyCallExpression) {
        return PyKnownDecoratorUtil.isResolvedToGeneratorBasedCoroutine((PyCallExpression)receiver, getResolveContext(), myTypeEvalContext);
      }
    }

    return false;
  }

  private boolean isEnabled(@NotNull PsiElement anchor) {
    if (myIsEnabled == null) {
      Boolean overridden = overriddenUnresolvedReferenceInspection(anchor.getContainingFile());
      myIsEnabled = Objects.requireNonNullElse(overridden, true);
    }
    return myIsEnabled;
  }

  private static @Nullable Boolean overriddenUnresolvedReferenceInspection(@NotNull PsiFile file) {
    return PyInspectionExtension.EP_NAME.getExtensionList().stream()
      .map(e -> e.overrideUnresolvedReferenceInspection(file))
      .filter(Objects::nonNull)
      .findFirst()
      .orElse(null);
  }

  public void highlightUnusedImports() {
    final List<PyInspectionExtension> extensions = PyInspectionExtension.EP_NAME.getExtensionList();
    final List<PsiElement> unused = collectUnusedImportElements();
    for (PsiElement element : unused) {
      if (extensions.stream().anyMatch(extension -> extension.ignoreUnused(element, myTypeEvalContext))) {
        continue;
      }
      if (element.getTextLength() > 0) {
        OptimizeImportsQuickFix fix = new OptimizeImportsQuickFix();
        registerProblem(element, PyPsiBundle.message("INSP.unused.import.statement"), ProblemHighlightType.LIKE_UNUSED_SYMBOL, null, fix);
      }
    }
  }

  public void highlightImportsInsideGuards() {
    HashSet<PyImportedNameDefiner> usedImportsInsideImportGuards = Sets.newHashSet(getImportsInsideGuard());
    usedImportsInsideImportGuards.retainAll(getUsedImports());

    for (PyImportedNameDefiner definer : usedImportsInsideImportGuards) {

      PyImportElement importElement = PyUtil.as(definer, PyImportElement.class);
      if (importElement == null) {
        continue;
      }
      final PyTargetExpression asElement = importElement.getAsNameElement();
      final PyElement toHighlight = asElement != null ? asElement : importElement.getImportReferenceExpression();
      registerProblem(toHighlight,
                      PyPsiBundle.message("INSP.try.except.import.error",
                                          importElement.getVisibleName()),
                      ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
    }
  }

  public void optimizeImports() {
    final List<PsiElement> elementsToDelete = collectUnusedImportElements();
    for (PsiElement element : elementsToDelete) {
      PyPsiUtils.assertValid(element);
      element.delete();
    }
  }

  protected List<PsiElement> collectUnusedImportElements() {
    if (getAllImports().isEmpty()) {
      return Collections.emptyList();
    }
    // PY-1315 Unused imports inspection shouldn't work in python REPL console
    final PyImportedNameDefiner first = getAllImports().iterator().next();
    if (first.getContainingFile() instanceof PyExpressionCodeFragment || PythonRuntimeService.getInstance().isInPydevConsole(first)) {
      return Collections.emptyList();
    }
    List<PsiElement> result = new ArrayList<>();

    Set<PyImportedNameDefiner> unusedImports = new HashSet<>(getAllImports());
    unusedImports.removeAll(getUsedImports());

    // Remove those unsed, that are reported to be skipped by extension points
    final Set<PyImportedNameDefiner> unusedImportToSkip = new HashSet<>();
    for (final PyImportedNameDefiner unusedImport : unusedImports) {
      if (PyInspectionExtension.EP_NAME.getExtensionList().stream().anyMatch(o -> o.ignoreUnusedImports(unusedImport))) {
        unusedImportToSkip.add(unusedImport);
      }
    }

    unusedImports.removeAll(unusedImportToSkip);

    Set<String> usedImportNames = new HashSet<>();
    for (PyImportedNameDefiner usedImport : getUsedImports()) {
      for (PyElement e : usedImport.iterateNames()) {
        usedImportNames.add(e.getName());
      }
    }

    Set<PyImportStatementBase> unusedStatements = new HashSet<>();
    QualifiedName packageQName = null;
    List<String> dunderAll = null;

    // TODO: Use strategies instead of pack of "continue"
    iterUnused:
    for (PyImportedNameDefiner unusedImport : unusedImports) {
      if (packageQName == null) {
        final PsiFile file = unusedImport.getContainingFile();
        if (file instanceof PyFile) {
          dunderAll = ((PyFile)file).getDunderAll();
        }
        if (file != null && PyUtil.isPackage(file)) {
          packageQName = QualifiedNameFinder.findShortestImportableQName(file);
        }
      }
      PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(unusedImport, PyImportStatementBase.class);
      if (importStatement != null && !unusedStatements.contains(importStatement) && !getUsedImports().contains(unusedImport)) {
        PyInspection inspection = getSession().getUserData(INSPECTION);
        assert inspection != null;
        if (inspection.isSuppressedFor(importStatement)) {
          continue;
        }
        // don't remove as unused imports in try/except statements
        if (PsiTreeUtil.getParentOfType(importStatement, PyTryExceptStatement.class) != null) {
          continue;
        }
        // Don't report conditional imports as unused
        if (PsiTreeUtil.getParentOfType(unusedImport, PyIfStatement.class) != null) {
          for (PyElement e : unusedImport.iterateNames()) {
            if (usedImportNames.contains(e.getName())) {
              continue iterUnused;
            }
          }
        }
        PsiFileSystemItem importedElement;
        if (unusedImport instanceof PyImportElement) {
          final PyImportElement importElement = (PyImportElement)unusedImport;
          final PsiElement element = importElement.resolve();
          if (element == null) {
            if (importElement.getImportedQName() != null) {
              //Mark import as unused even if it can't be resolved
              if (PyUnresolvedReferencesVisitor.areAllImportsUnused(importStatement, unusedImports)) {
                result.add(importStatement);
              }
              else {
                result.add(importElement);
              }
            }
            continue;
          }
          if (dunderAll != null && dunderAll.contains(importElement.getVisibleName())) {
            continue;
          }
          importedElement = element.getContainingFile();
        }
        else {
          assert importStatement instanceof PyFromImportStatement;
          importedElement = ((PyFromImportStatement)importStatement).resolveImportSource();
          if (importedElement == null) {
            continue;
          }
        }
        if (packageQName != null && importedElement != null) {
          final QualifiedName importedQName = QualifiedNameFinder.findShortestImportableQName(importedElement);
          if (importedQName != null && importedQName.matchesPrefix(packageQName)) {
            continue;
          }
        }
        if (unusedImport instanceof PyStarImportElement || areAllImportsUnused(importStatement, unusedImports)) {
          unusedStatements.add(importStatement);
          result.add(importStatement);
        }
        else {
          result.add(unusedImport);
        }
      }
    }
    return result;
  }

  private static boolean areAllImportsUnused(PyImportStatementBase importStatement, Set<PyImportedNameDefiner> unusedImports) {
    final PyImportElement[] elements = importStatement.getImportElements();
    for (PyImportElement element : elements) {
      if (!unusedImports.contains(element)) {
        return false;
      }
    }
    return true;
  }

  boolean ignoreUnresolved(@NotNull PyElement node, @NotNull PsiReference reference) {
    boolean ignoreUnresolved = false;
    for (PyInspectionExtension extension : PyInspectionExtension.EP_NAME.getExtensionList()) {
      if (extension.ignoreUnresolvedReference(node, reference, myTypeEvalContext)) {
        ignoreUnresolved = true;
        break;
      }
    }
    return ignoreUnresolved;
  }

  Collection<PyImportedNameDefiner> getImportsInsideGuard() {
    return Collections.unmodifiableCollection(myImportsInsideGuard);
  }

  Collection<PyImportedNameDefiner> getAllImports() {
    return Collections.unmodifiableCollection(myAllImports);
  }

  Collection<PyImportedNameDefiner> getUsedImports() {
    return Collections.unmodifiableCollection(myUsedImports);
  }

  @Nullable
  private static PyExceptPart getImportErrorGuard(PyElement node) {
    final PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(node, PyImportStatementBase.class);
    if (importStatement != null) {
      final PyTryPart tryPart = PsiTreeUtil.getParentOfType(node, PyTryPart.class);
      if (tryPart != null) {
        final PyTryExceptStatement tryExceptStatement = PsiTreeUtil.getParentOfType(tryPart, PyTryExceptStatement.class);
        if (tryExceptStatement != null) {
          for (PyExceptPart exceptPart : tryExceptStatement.getExceptParts()) {
            final PyExpression expr = exceptPart.getExceptClass();
            if (expr != null && "ImportError".equals(expr.getName())) {
              return exceptPart;
            }
          }
        }
      }
    }
    return null;
  }

  private static boolean isContainingFileImportAllowed(PyElement node, PsiFile target) {
    return PyImportStatementNavigator.getImportStatementByElement(node) == null && target.getName().equals(PyNames.INIT_DOT_PY);
  }

  private static boolean shouldSkipMissingWriteInsideGuard(@NotNull PyExceptPart guard, @NotNull String name) {
    return isDefinedInParentScope(name, guard) ||
           PyBuiltinCache.getInstance(guard).getByName(name) != null ||
           controlFlowAlwaysTerminatesInsideGuard(guard);
  }

  private static boolean isDefinedInParentScope(@NotNull String name, @NotNull PsiElement anchor) {
    return ScopeUtil.getDeclarationScopeOwner(ScopeUtil.getScopeOwner(anchor), name) != null;
  }

  private static boolean controlFlowAlwaysTerminatesInsideGuard(@NotNull PyExceptPart guard) {
    final ScopeOwner owner = ScopeUtil.getScopeOwner(guard);
    if (owner == null) return false;
    final ControlFlow flow = ControlFlowCache.getControlFlow(owner);
    final Instruction[] instructions = flow.getInstructions();
    final int start = ControlFlowUtil.findInstructionNumberByElement(instructions, guard.getExceptClass());
    if (start <= 0) return false;
    final Ref<Boolean> canEscapeGuard = Ref.create(false);
    ControlFlowUtil.process(instructions, start, instruction -> {
      final PsiElement e = instruction.getElement();
      if (e != null && !PsiTreeUtil.isAncestor(guard, e, true)) {
        canEscapeGuard.set(true);
        return false;
      }
      return true;
    });
    return !canEscapeGuard.get();
  }

  @Nullable
  private static PyExpression getReferenceQualifier(@NotNull PsiReference reference) {
    final PsiElement element = reference.getElement();

    if (element instanceof PyQualifiedExpression) {
      final PyExpression qualifier = ((PyQualifiedExpression)element).getQualifier();
      if (qualifier != null) {
        return qualifier;
      }
    }

    if (reference instanceof PyFromImportNameReference) {
      final PyFromImportStatement statement = PsiTreeUtil.getParentOfType(element, PyFromImportStatement.class);
      if (statement != null) {
        final PyReferenceExpression source = statement.getImportSource();
        if (source != null) {
          return source;
        }
      }
    }

    return null;
  }

  /**
   * Return the canonical qualified names for a reference (even for an unresolved one).
   * If reference is qualified and its qualifier has union type, all possible canonical names will be returned.
   */
  @NotNull
  private static List<QualifiedName> getCanonicalNames(@NotNull PsiReference reference, @NotNull TypeEvalContext context) {
    final PsiElement element = reference.getElement();
    final List<QualifiedName> result = new SmartList<>();
    if (reference instanceof PyOperatorReference && element instanceof PyQualifiedExpression) {
      final PyExpression receiver = ((PyOperatorReference)reference).getReceiver();
      if (receiver != null) {
        final PyType type = context.getType(receiver);
        if (type instanceof PyClassType) {
          final String methodName = ((PyQualifiedExpression)element).getReferencedName();
          ContainerUtil.addIfNotNull(result, extractAttributeQNameFromClassType(methodName, (PyClassType)type));
        }
      }
    }
    else if (element instanceof PyReferenceExpression) {
      final PyReferenceExpression expr = (PyReferenceExpression)element;
      final PyExpression qualifier = expr.getQualifier();
      final String exprName = expr.getName();
      if (exprName != null) {
        if (qualifier != null) {
          final PyType qualifierType = context.getType(qualifier);
          PyTypeUtil.toStream(qualifierType)
            .map(type -> {
              if (type instanceof PyClassType) {
                return extractAttributeQNameFromClassType(exprName, (PyClassType)type);
              }
              else if (type instanceof PyModuleType) {
                final PyFile file = ((PyModuleType)type).getModule();
                final QualifiedName name = QualifiedNameFinder.findCanonicalImportPath(file, element);
                if (name != null) {
                  return name.append(exprName);
                }
              }
              else if (type instanceof PyImportedModuleType) {
                final PyImportedModule module = ((PyImportedModuleType)type).getImportedModule();
                final PsiElement resolved = module.resolve();
                if (resolved != null) {
                  final QualifiedName path = QualifiedNameFinder.findCanonicalImportPath(resolved, element);
                  if (path != null) {
                    return path.append(exprName);
                  }
                }
              }
              else if (type instanceof PyFunctionType) {
                final PyCallable callable = ((PyFunctionType)type).getCallable();
                final String callableName = callable.getName();
                if (callableName != null) {
                  final QualifiedName path = QualifiedNameFinder.findCanonicalImportPath(callable, element);
                  if (path != null) {
                    return path.append(QualifiedName.fromComponents(callableName, exprName));
                  }
                }
              }
              return null;
            })
            .nonNull()
            .into(result);
        }
        else {
          final PsiElement parent = element.getParent();
          if (parent instanceof PyImportElement) {
            final PyImportStatementBase importStmt = PsiTreeUtil.getParentOfType(parent, PyImportStatementBase.class);
            if (importStmt instanceof PyImportStatement) {
              ContainerUtil.addIfNotNull(result, QualifiedName.fromComponents(exprName));
            }
            else if (importStmt instanceof PyFromImportStatement) {
              final PsiElement resolved = ((PyFromImportStatement)importStmt).resolveImportSource();
              if (resolved != null) {
                final QualifiedName path = QualifiedNameFinder.findCanonicalImportPath(resolved, element);
                if (path != null) {
                  ContainerUtil.addIfNotNull(result, path.append(exprName));
                }
              }
            }
          }
          else {
            final QualifiedName path = QualifiedNameFinder.findCanonicalImportPath(element, element);
            if (path != null) {
              ContainerUtil.addIfNotNull(result, path.append(exprName));
            }
          }
        }
      }
    }
    else if (reference instanceof DocStringParameterReference) {
      ContainerUtil.addIfNotNull(result, QualifiedName.fromDottedString(reference.getCanonicalText()));
    }
    return result;
  }

  private static QualifiedName extractAttributeQNameFromClassType(String exprName, PyClassType type) {
    final String name = type.getClassQName();
    if (name != null) {
      return QualifiedName.fromDottedString(name).append(exprName);
    }
    return null;
  }

  private static boolean hasUnresolvedDynamicMember(@NotNull final PyClassType type,
                                                  PsiReference reference,
                                                  @NotNull final String name, TypeEvalContext typeEvalContext) {

    final List<PyClassType> types = new ArrayList<>(Collections.singletonList(type));
    types.addAll(FluentIterable.from(type.getAncestorTypes(typeEvalContext)).filter(PyClassType.class).toList());


    for (final PyClassType typeToCheck : types) {
      for (PyClassMembersProvider provider : PyClassMembersProvider.EP_NAME.getExtensionList()) {
        final Collection<PyCustomMember> resolveResult = provider.getMembers(typeToCheck, reference.getElement(), typeEvalContext);
        for (PyCustomMember member : resolveResult) {
          if (member.getName().equals(name)) return true;
        }
      }
    }

    return false;
  }

  Iterable<LocalQuickFix> getInstallPackageQuickFixes(@NotNull PyElement node,
                                                      @NotNull PsiReference reference,
                                                      String refName) {
    return Collections.emptyList();
  }

  Iterable<LocalQuickFix> getAddIgnoredIdentifierQuickFixes(List<QualifiedName> qualifiedNames) {
    return Collections.emptyList();
  }

  Iterable<LocalQuickFix> getImportStatementQuickFixes(PsiElement element) {
    return Collections.emptyList();
  }

  LocalQuickFix getAddParameterQuickFix(String refName, PyReferenceExpression expr) {
    final PyFunction parentFunction = PsiTreeUtil.getParentOfType(expr, PyFunction.class);
    final PyDecorator decorator = PsiTreeUtil.getParentOfType(expr, PyDecorator.class);
    final PyAnnotation annotation = PsiTreeUtil.getParentOfType(expr, PyAnnotation.class);
    final PyImportStatement importStatement = PsiTreeUtil.getParentOfType(expr, PyImportStatement.class);
    if (parentFunction != null && decorator == null && annotation == null && importStatement == null) {
      return new UnresolvedReferenceAddParameterQuickFix(refName);
    }
    return null;
  }

  LocalQuickFix getCreateFunctionQuickFix(PyReferenceExpression expr) {
    PyCallExpression callExpression = PsiTreeUtil.getParentOfType(expr, PyCallExpression.class);
    if (callExpression != null && (!(callExpression.getCallee() instanceof PyQualifiedExpression) ||
                                   ((PyQualifiedExpression)callExpression.getCallee()).getQualifier() == null)) {
      return new UnresolvedRefCreateFunctionQuickFix(callExpression, expr);
    }
    return null;
  }

  LocalQuickFix getTrueFalseQuickFix(PyReferenceExpression expr, String refText) {
    if (refText.equals("true") || refText.equals("false")) {
      return new UnresolvedRefTrueFalseQuickFix(expr);
    }
    return null;
  }

  Iterable<LocalQuickFix> getCreateMemberFromUsageFixes(PyType type, PsiReference reference, String refText) {
    List<LocalQuickFix> result = new ArrayList<>();
    PsiElement element = reference.getElement();
    if (type instanceof PyClassTypeImpl) {
      PyClass cls = ((PyClassType)type).getPyClass();
      if (!PyBuiltinCache.getInstance(element).isBuiltin(cls)) {
        if (element.getParent() instanceof PyCallExpression) {
          result.add(new AddMethodQuickFix(refText, cls.getName(), true));
        }
        else if (!(reference instanceof PyOperatorReference)) {
          result.add(new AddFieldQuickFix(refText, "None", type.getName(), true));
        }
      }
    }
    else if (type instanceof PyModuleType) {
      PyFile file = ((PyModuleType)type).getModule();
      LocalQuickFix createClassQuickFix = getCreateClassFix(refText, element);
      if (createClassQuickFix != null) {
        result.add(createClassQuickFix);
      }
      else {
        result.add(new AddFunctionQuickFix(refText, file.getName()));
      }
    }
    return result;
  }


  Iterable<LocalQuickFix> getAddSelfFixes(TypeEvalContext typeEvalContext, PyElement node, PyReferenceExpression expr) {
    List<LocalQuickFix> result = new ArrayList<>();
    final PyClass containedClass = PsiTreeUtil.getParentOfType(node, PyClass.class);
    final PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class);
    if (containedClass != null && function != null) {
      final PyParameter[] parameters = function.getParameterList().getParameters();
      if (parameters.length == 0) return Collections.emptyList();
      final String qualifier = parameters[0].getText();
      final PyDecoratorList decoratorList = function.getDecoratorList();
      boolean isClassMethod = false;
      if (decoratorList != null) {
        for (PyDecorator decorator : decoratorList.getDecorators()) {
          final PyExpression callee = decorator.getCallee();
          if (callee != null && PyNames.CLASSMETHOD.equals(callee.getText())) {
            isClassMethod = true;
          }
        }
      }
      for (PyTargetExpression target : containedClass.getInstanceAttributes()) {
        if (!isClassMethod && Comparing.strEqual(node.getName(), target.getName())) {
          result.add(new UnresolvedReferenceAddSelfQuickFix(expr, qualifier));
        }
      }
      for (PyStatement statement : containedClass.getStatementList().getStatements()) {
        if (statement instanceof PyAssignmentStatement) {
          PyExpression lhsExpression = ((PyAssignmentStatement)statement).getLeftHandSideExpression();
          if (lhsExpression != null && lhsExpression.getText().equals(expr.getText())) {
            PyExpression assignedValue = ((PyAssignmentStatement)statement).getAssignedValue();
            if (assignedValue instanceof PyCallExpression) {
              PyType type = typeEvalContext.getType(assignedValue);
              if (type instanceof PyClassTypeImpl) {
                if (((PyCallExpression)assignedValue).isCalleeText(PyNames.PROPERTY)) {
                  result.add(new UnresolvedReferenceAddSelfQuickFix(expr, qualifier));
                }
              }
            }
          }
        }
      }
      for (PyFunction method : containedClass.getMethods()) {
        if (expr.getText().equals(method.getName())) {
          result.add(new UnresolvedReferenceAddSelfQuickFix(expr, qualifier));
        }
      }
    }
    return result;
  }

  protected Iterable<LocalQuickFix> getAutoImportFixes(PyElement node, PsiReference reference, PsiElement element) {
    return Collections.emptyList();
  }

  LocalQuickFix getCreateClassFix(@NonNls String refText, PsiElement element) {
    if (refText.length() > 2 && Character.isUpperCase(refText.charAt(0)) && !StringUtil.toUpperCase(refText).equals(refText)) {
      PsiElement anchor = element;
      if (element instanceof PyQualifiedExpression) {
        PyExpression qualifier = ((PyQualifiedExpression)element).getQualifier();
        if (qualifier == null) {
          final PyFromImportStatement fromImport = PsiTreeUtil.getParentOfType(element, PyFromImportStatement.class);
          if (fromImport != null) qualifier = fromImport.getImportSource();
        }
        if (qualifier != null) {
          final PyType type = myTypeEvalContext.getType(qualifier);
          if (type instanceof PyModuleType) {
            anchor = ((PyModuleType)type).getModule();
          }
          else {
            anchor = null;
          }
        }
        if (anchor != null) {
          return new CreateClassQuickFix(refText, anchor);
        }
      }
    }
    return null;
  }

  Iterable<LocalQuickFix> getPluginQuickFixes(PsiReference reference) {
    return Collections.emptyList();
  }
}
