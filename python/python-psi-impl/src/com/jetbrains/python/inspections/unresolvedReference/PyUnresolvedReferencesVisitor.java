// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.unresolvedReference;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyCustomType;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.codeInsight.PySubstitutionChunkReference;
import com.jetbrains.python.codeInsight.controlflow.PyDataFlowKt;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.documentation.docstrings.DocStringParameterReference;
import com.jetbrains.python.documentation.docstrings.DocStringTypeReference;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.inspections.PyInspectionVisitor;
import com.jetbrains.python.inspections.quickfix.*;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyCallExpressionNavigator;
import com.jetbrains.python.psi.impl.PyImportStatementNavigator;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.references.PyFromImportNameReference;
import com.jetbrains.python.psi.impl.references.PyImportReference;
import com.jetbrains.python.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.psi.impl.references.hasattr.PyHasAttrHelper;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.types.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.*;

import static com.jetbrains.python.PyNames.END_WILDCARD;
import static com.jetbrains.python.psi.PyUtil.as;
import static com.jetbrains.python.psi.impl.stubs.PyVersionSpecificStubBaseKt.evaluateVersionsForElement;

public abstract class PyUnresolvedReferencesVisitor extends PyInspectionVisitor {
  private final ImmutableSet<String> myIgnoredIdentifiers;
  private final Version myVersion;
  private volatile Boolean myIsEnabled = null;
  protected final List<PyPackageInstallAllProblemInfo> myUnresolvedRefs = Collections.synchronizedList(new ArrayList<>());

  protected PyUnresolvedReferencesVisitor(@Nullable ProblemsHolder holder,
                                          @NotNull List<String> ignoredIdentifiers,
                                          @NotNull TypeEvalContext context,
                                          @NotNull LanguageLevel languageLevel) {
    super(holder, context);
    myIgnoredIdentifiers = ImmutableSet.copyOf(ignoredIdentifiers);
    myVersion = new Version(languageLevel.getMajorVersion(), languageLevel.getMinorVersion(), 0);
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
  public void visitPyElement(final @NotNull PyElement node) {
    super.visitPyElement(node);
    if (node instanceof PyReferenceOwner referenceOwner) {
      final PyResolveContext resolveContext = PyResolveContext.defaultContext(myTypeEvalContext);
      processReference(node, referenceOwner.getReference(resolveContext));
    }
    else {
      for (final PsiReference reference : node.getReferences()) {
        processReference(node, reference);
      }
    }
  }

  private void processReference(@NotNull PyElement node, @Nullable PsiReference reference) {
    if (!isEnabled(node) || reference == null || reference.isSoft()) {
      return;
    }
    final PyExceptPart guard = getImportErrorGuard(node);
    if (guard != null) {
      return;
    }
    if (node instanceof PyQualifiedExpression qExpr) {
      final PyExpression qualifier = qExpr.getQualifier();
      final String name = node.getName();
      if (qualifier != null && name != null && PyHasAttrHelper.INSTANCE.getNamesFromHasAttrs(node, qualifier).contains(name)) {
        return;
      }
    }
    PsiElement target = null;
    final boolean unresolved;
    if (reference instanceof PsiPolyVariantReference poly) {
      final ResolveResult[] resolveResults = poly.multiResolve(false);
      unresolved = (resolveResults.length == 0);
      for (ResolveResult resolveResult : resolveResults) {
        if (target == null && resolveResult.isValidResult()) {
          target = resolveResult.getElement();
        }
      }
    }
    else {
      target = reference.resolve();
      unresolved = (target == null);
    }
    if (unresolved) {
      boolean ignoreUnresolved = ignoreUnresolved(node, reference) || !evaluateVersionsForElement(node).contains(myVersion);
      if (!ignoreUnresolved) {
        HighlightSeverity severity = reference instanceof PsiReferenceEx
                                           ? ((PsiReferenceEx)reference).getUnresolvedHighlightSeverity(myTypeEvalContext)
                                           : HighlightSeverity.ERROR;
        if (severity == null) {
          if (isAwaitCallToImportedNonAsyncFunction(reference)) {
            // special case: type of prefixExpression.getQualifier() is null but we want to check whether the called function is async
            severity = HighlightSeverity.WEAK_WARNING;
          }
        }
        if (severity == null) return;
        registerUnresolvedReferenceProblem(node, reference, severity);
      }
    }
    else if (reference instanceof PyImportReference &&
             target == reference.getElement().getContainingFile() &&
             !isContainingFileImportAllowed(node, (PsiFile)target)) {
      registerProblem(node, PyPsiBundle.message("INSP.unresolved.refs.import.resolves.to.its.containing.file"));
    }
    else if (PyUnionType.isStrictSemanticsEnabled() && node instanceof PyQualifiedExpression qualifiedExpression) {
      String referencedName = qualifiedExpression.getReferencedName();
      PyExpression qualifier = qualifiedExpression.getQualifier();
      if (referencedName != null && qualifier != null) {
        PyType qualifierType = myTypeEvalContext.getType(qualifier);
        if (qualifierType instanceof PyUnionType unionType) {
          PyType unionMemberMissingAttr = findStrictUnionMemberMissingAttribute(unionType, reference, referencedName);
          if (unionMemberMissingAttr != null) {
            String unionTypeRender = PythonDocumentationProvider.getTypeName(qualifierType, myTypeEvalContext);
            String unionMemberRender = PythonDocumentationProvider.getTypeName(unionMemberMissingAttr, myTypeEvalContext);
            registerProblem(
              node,
              PyPsiBundle.message("INSP.unresolved.refs.unresolved.attribute.in.union.type", unionMemberRender, unionTypeRender, referencedName),
              ProblemHighlightType.WEAK_WARNING,
              null,
              reference.getRangeInElement()
            );
          }
        }
      }
    }
  }

  private boolean isAwaitCallToImportedNonAsyncFunction(@NotNull PsiReference reference) {
    if (reference.getElement() instanceof PyPrefixExpression prefixExpression
        && PyNames.DUNDER_AWAIT.equals(prefixExpression.getOperator().getSpecialMethodName())
        && getReferenceQualifier(reference) instanceof PyCallExpression callExpression) {

      @NotNull List<@NotNull PyCallable> callees =
        callExpression.multiResolveCalleeFunction(PyResolveContext.defaultContext(myTypeEvalContext));

      if (callees.isEmpty()) {
        return false;
      }
      for (PyCallable callee : callees) {
        if (callee instanceof PyFunction pyFunction && pyFunction.isAsync()) {
          return false;
        }
      }
      return true; // no signature is declared async -> warning
    }
    return false;
  }

  private void registerUnresolvedReferenceProblem(@NotNull PyElement node, final @NotNull PsiReference reference,
                                                  @NotNull HighlightSeverity severity) {
    if (reference instanceof DocStringTypeReference) {
      return;
    }
    String description = null;
    PsiElement element = reference.getElement();
    final String text = element.getText();
    TextRange rangeInElement = reference.getRangeInElement();
    String refText = text;  // text of the part we're working with
    if (rangeInElement.getStartOffset() >= 0 && rangeInElement.getEndOffset() > 0) {
      refText = rangeInElement.substring(text);
    }

    final String refName = (element instanceof PyQualifiedExpression) ? ((PyQualifiedExpression)element).getReferencedName() : refText;
    // Empty text, nothing to highlight
    if (StringUtil.isEmpty(refName)) {
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
    if (element instanceof PyReferenceExpression expr) {
      if (PyNames.COMPARISON_OPERATORS.contains(refName)) {
        return;
      }
      if (!expr.isQualified()) {
        if (PyDataFlowKt.isUnreachableForInspection(expr, myTypeEvalContext)) {
          return;
        }
        ContainerUtil.addIfNotNull(fixes, getTrueFalseQuickFix(refText));
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
        if (PyNames.COMPARISON_OPERATORS.contains(refName)) {
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
          if (type instanceof PyClassType classType) {
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
            PyType unionMemberWithoutAttr = findStrictUnionMemberMissingAttribute(type, reference, refName);
            if (unionMemberWithoutAttr != null) {
              String unionTypeRender = PythonDocumentationProvider.getTypeName(type, myTypeEvalContext);
              String unionMemberRender = PythonDocumentationProvider.getTypeName(unionMemberWithoutAttr, myTypeEvalContext);
              description =
                PyPsiBundle.message("INSP.unresolved.refs.unresolved.attribute.in.union.type", unionMemberRender, unionTypeRender, refName);
              severity = HighlightSeverity.WEAK_WARNING;
            }
            else {
              description = PyPsiBundle.message("INSP.unresolved.refs.cannot.find.reference.in.type", refText, type.getName());
            }
          }
          markedQualified = true;
        }
        else {
          if (isAwaitCallToImportedNonAsyncFunction(reference)) {
            description = PyPsiBundle.message("INSP.await.call.on.imported.untyped.function", qualifier.getText());
            node = qualifier; // show warning on the function call
            rangeInElement = TextRange.create(0, qualifier.getTextRange().getLength());
            markedQualified = true;
          }
        }
      }
      if (!markedQualified) {
        description = PyPsiBundle.message("INSP.unresolved.refs.unresolved.reference", refText);

        ContainerUtil.addAll(fixes, getAutoImportFixes(node, reference, element));
        ContainerUtil.addIfNotNull(fixes, getCreateClassFix(refText, element));
      }
    }
    ProblemHighlightType hlType;
    if (severity == HighlightSeverity.WARNING) {
      hlType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
    }
    if (severity == HighlightSeverity.WEAK_WARNING) {
      hlType = ProblemHighlightType.WEAK_WARNING;
    }
    else if (severity == HighlightSeverity.ERROR) {
      hlType = ProblemHighlightType.GENERIC_ERROR;
    }
    else {
      hlType = ProblemHighlightType.LIKE_UNKNOWN_SYMBOL;
    }

    ContainerUtil.addAll(fixes, getImportStatementQuickFixes(element));
    ContainerUtil.addAll(fixes, getAddIgnoredIdentifierQuickFixes(qualifiedNames));
    var installPackageQuickFixes = getInstallPackageQuickFixes(node, reference, refName);
    var isAddedToInstallAllFix = false;
    if (!installPackageQuickFixes.isEmpty()) {
      ContainerUtil.addAll(fixes, installPackageQuickFixes);
      PyPackageInstallAllProblemInfo problemInfo =
        new PyPackageInstallAllProblemInfo(node, description, hlType, refName, fixes);
      myUnresolvedRefs.add(problemInfo);
      isAddedToInstallAllFix = true;
    }

    if (reference instanceof PySubstitutionChunkReference) {
      return;
    }

    getPluginQuickFixes(fixes, reference);
    if (!isAddedToInstallAllFix) {
      registerProblem(node, description, hlType, null, rangeInElement, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
    }
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

  private boolean ignoreUnresolvedMemberForType(@Nullable PyType type, @NotNull PsiReference reference, @NotNull String name) {
    if (type instanceof PyTypeVarType typeVarType) {
      return typeVarType.getBound() == null && typeVarType.getDefaultType() == null && typeVarType.getConstraints().isEmpty();
    }
    if (type instanceof PyUnionType unionType) {
      if (PyUnionType.isStrictSemanticsEnabled()) {
        // If strict unions are enabled, we should report an error even if a union contains Any, e.g. in
        // x: int | Any
        // x.foo()  # 'foo' access should be reported despite Any
        return findStrictUnionMemberMissingAttribute(unionType, reference, name) == null;
      }
      return ContainerUtil.exists(unionType.getMembers(), member -> ignoreUnresolvedMemberForType(member, reference, name));
    }
    if (type instanceof PyUnsafeUnionType weakUnionType) {
      return ContainerUtil.exists(weakUnionType.getMembers(), member -> ignoreUnresolvedMemberForType(member, reference, name));
    }
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

  private @Nullable PyType findStrictUnionMemberMissingAttribute(@NotNull PyType type, @NotNull PsiReference ref, @NotNull String name) {
    if (!(type instanceof PyUnionType unionType) || !PyUnionType.isStrictSemanticsEnabled()) {
      return null;
    }
    // In cases like the following (see PyUnusedImportTest#testModuleAndSubmodule):
    //
    // import pkg.mod
    // import pkg
    // pkg.mod
    //     ^
    //
    // The type of `pkg` is a union of PyModuleType('pkg/__init__.py') and PyImportedModuleType('import pkg').
    // Only the last one owns the attribute `mod` directly, and the first needs a location to inspect imports
    // of this module in the file the original reference belongs to.
    PyExpression location = as(ref.getElement(), PyExpression.class);
    return ContainerUtil.find(unionType.getMembers(), t -> {
      if (t == null || ignoreUnresolvedMemberForType(t, ref, name)) return false;
      return ContainerUtil.isEmpty(t.resolveMember(name, location, AccessDirection.READ, getResolveContext()));
    });
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

  public void addInstallAllImports() {
    Set<String> refNames = ContainerUtil.map2Set(myUnresolvedRefs, it -> it.getRefName());
    LocalQuickFix installAllPackageQuickFixes = getInstallAllPackagesQuickFix();
    for (PyPackageInstallAllProblemInfo unresolved : myUnresolvedRefs) {
      var quickFixes = unresolved.getFixes();
      if (refNames.size() > 1) {
        ContainerUtil.addIfNotNull(quickFixes, installAllPackageQuickFixes);
      }
      registerProblem(unresolved.getPsiElement(), unresolved.getDescriptionTemplate(), unresolved.getHighlightType(), null,
                      quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
    }
  }

  private boolean ignoreUnresolved(@NotNull PyElement node, @NotNull PsiReference reference) {
    boolean ignoreUnresolved = false;
    for (PyInspectionExtension extension : PyInspectionExtension.EP_NAME.getExtensionList()) {
      if (extension.ignoreUnresolvedReference(node, reference, myTypeEvalContext)) {
        ignoreUnresolved = true;
        break;
      }
    }
    return ignoreUnresolved;
  }

  private static @Nullable PyExceptPart getImportErrorGuard(PyElement node) {
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

  private static @Nullable PyExpression getReferenceQualifier(@NotNull PsiReference reference) {
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
  private static @NotNull List<QualifiedName> getCanonicalNames(@NotNull PsiReference reference, @NotNull TypeEvalContext context) {
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
    else if (element instanceof PyReferenceExpression expr) {
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

  protected @NotNull List<LocalQuickFix> getInstallPackageQuickFixes(@NotNull PyElement node,
                                                                     @NotNull PsiReference reference,
                                                                     String refName) {
    return Collections.emptyList();
  }

  protected @Nullable LocalQuickFix getInstallAllPackagesQuickFix() {
    return null;
  }

  private static @Nullable LocalQuickFix getCreateFunctionQuickFix(@NotNull PyReferenceExpression expr) {
    PyCallExpression callExpression = PyCallExpressionNavigator.getPyCallExpressionByCallee(expr);
    if (callExpression != null && (!(callExpression.getCallee() instanceof PyQualifiedExpression) ||
                                   ((PyQualifiedExpression)callExpression.getCallee()).getQualifier() == null)) {
      return new UnresolvedRefCreateFunctionQuickFix(expr);
    }
    return null;
  }

  protected @NotNull List<LocalQuickFix> getAddIgnoredIdentifierQuickFixes(List<QualifiedName> qualifiedNames) {
    return Collections.emptyList();
  }

  protected @NotNull List<LocalQuickFix> getImportStatementQuickFixes(PsiElement element) {
    return Collections.emptyList();
  }

  private static @Nullable LocalQuickFix getAddParameterQuickFix(String refName, PyReferenceExpression expr) {
    final PyFunction parentFunction = PsiTreeUtil.getParentOfType(expr, PyFunction.class);
    final PyDecorator decorator = PsiTreeUtil.getParentOfType(expr, PyDecorator.class);
    final PyAnnotation annotation = PsiTreeUtil.getParentOfType(expr, PyAnnotation.class);
    final PyImportStatement importStatement = PsiTreeUtil.getParentOfType(expr, PyImportStatement.class);
    if (parentFunction != null && decorator == null && annotation == null && importStatement == null) {
      return new UnresolvedReferenceAddParameterQuickFix(refName);
    }
    return null;
  }

  private static @Nullable LocalQuickFix getTrueFalseQuickFix(@NotNull String refText) {
    if (refText.equals("true") || refText.equals("false")) {
      return new UnresolvedRefTrueFalseQuickFix(refText);
    }
    return null;
  }

  private @Nullable LocalQuickFix getCreateClassFix(@NonNls String refText, PsiElement element) {
    if (refText.length() > 2 && Character.isUpperCase(refText.charAt(0)) && !StringUtil.toUpperCase(refText).equals(refText)) {
      if (element instanceof PyQualifiedExpression) {
        PyExpression qualifier = ((PyQualifiedExpression)element).getQualifier();
        if (qualifier == null) {
          final PyFromImportStatement fromImport = PsiTreeUtil.getParentOfType(element, PyFromImportStatement.class);
          if (fromImport != null) qualifier = fromImport.getImportSource();
        }
        PsiFile destination = null;
        if (qualifier != null) {
          final PyType type = myTypeEvalContext.getType(qualifier);
          if (type instanceof PyModuleType) {
            destination = ((PyModuleType)type).getModule();
          }
          else {
            return null;
          }
        }
        if (destination == null) {
          InjectedLanguageManager injectionManager = InjectedLanguageManager.getInstance(element.getProject());
          PsiLanguageInjectionHost injectionHost = injectionManager.getInjectionHost(element);
          destination = ObjectUtils.chooseNotNull(injectionHost, element).getContainingFile();
        }
        return new CreateClassQuickFix(refText, destination);
      }
    }
    return null;
  }

  private @NotNull List<LocalQuickFix> getCreateMemberFromUsageFixes(PyType type, PsiReference reference, String refText) {
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


  private static @NotNull List<LocalQuickFix> getAddSelfFixes(TypeEvalContext typeEvalContext, PyElement node, PyReferenceExpression expr) {
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

  protected List<LocalQuickFix> getAutoImportFixes(PyElement node, PsiReference reference, PsiElement element) {
    return Collections.emptyList();
  }

  private static boolean hasUnresolvedDynamicMember(final @NotNull PyClassType type,
                                                    PsiReference reference,
                                                    final @NotNull String name, TypeEvalContext typeEvalContext) {

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

  @VisibleForTesting
  public void getPluginQuickFixes(List<LocalQuickFix> fixes, PsiReference reference) {
    // Nothing.
  }
}
