// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.unresolvedReference

import com.google.common.collect.ImmutableSet
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.QualifiedName
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.addIfNotNull
import com.jetbrains.python.PyCustomType
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyNames.END_WILDCARD
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.PyDunderMatchArgsReference
import com.jetbrains.python.codeInsight.PySubstitutionChunkReference
import com.jetbrains.python.codeInsight.controlflow.Reachability
import com.jetbrains.python.codeInsight.controlflow.getReachabilityForInspection
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.documentation.docstrings.DocStringTypeReference
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.inspections.quickfix.PyRenameUnresolvedRefQuickFix
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PsiReferenceEx
import com.jetbrains.python.psi.PyAugAssignmentStatement
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyCallSiteExpression
import com.jetbrains.python.psi.PyCallable
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyExceptPart
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyIfStatement
import com.jetbrains.python.psi.PyImportElement
import com.jetbrains.python.psi.PyImportStatementBase
import com.jetbrains.python.psi.PyKeywordPattern
import com.jetbrains.python.psi.PyKnownDecoratorUtil
import com.jetbrains.python.psi.PyPrefixExpression
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyReferenceOwner
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTryExceptStatement
import com.jetbrains.python.psi.PyTryPart
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyBuiltinCache.Companion.getInstance
import com.jetbrains.python.psi.impl.PyImportStatementNavigator
import com.jetbrains.python.psi.impl.references.PyFromImportNameReference
import com.jetbrains.python.psi.impl.references.PyImportReference
import com.jetbrains.python.psi.impl.references.PyOperatorReference
import com.jetbrains.python.psi.impl.references.hasattr.PyHasAttrHelper.getNamesFromHasAttrs
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassMembersProvider
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyCompositeType
import com.jetbrains.python.psi.types.PyFunctionTypeImpl
import com.jetbrains.python.psi.types.PyImportedModuleType
import com.jetbrains.python.psi.types.PyModuleType
import com.jetbrains.python.psi.types.PySelfType
import com.jetbrains.python.psi.types.PyStructuralType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker.definesGetAttr
import com.jetbrains.python.psi.types.PyTypeChecker.isUnknown
import com.jetbrains.python.psi.types.PyTypeChecker.overridesGetAttr
import com.jetbrains.python.psi.types.PyTypeUtil.toStream
import com.jetbrains.python.psi.types.PyTypeVarType
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import one.util.streamex.StreamEx
import org.jetbrains.annotations.VisibleForTesting
import java.util.function.Function

abstract class PyUnresolvedReferencesVisitor @JvmOverloads protected constructor(
  holder: ProblemsHolder?,
  ignoredIdentifiers: List<String>,
  context: TypeEvalContext,
  @Suppress("UNUSED_PARAMETER") languageLevel: LanguageLevel,  // kept for binary compat with Java subclasses
  private val myStrictClassAttributes: Boolean,
  private val myStrictInstanceAttributes: Boolean = false,
) : PyInspectionVisitor(holder, context) {
  private val myIgnoredIdentifiers: ImmutableSet<String> = ImmutableSet.copyOf(ignoredIdentifiers)

  @Volatile
  private var myIsEnabled: Boolean? = null
  protected val myUnresolvedRefs: MutableList<PyPackageInstallAllProblemInfo> = ArrayList()

  override fun visitPyTargetExpression(node: PyTargetExpression) {
    // Augmented assignments (e.g., `x += 1`) do have a target expression,
    // but for historical reasons it is not represented in the PSI, so delegate to the base visitor for general reference checks
    if (node.getParent() is PyAugAssignmentStatement) {
      super.visitPyTargetExpression(node)
    }

    checkAttributeAssignment(node)
  }

  private fun checkAttributeAssignment(node: PyQualifiedExpression) {
    val qualifier = node.qualifier ?: return
    val attrName = node.referencedName ?: return
    val type = replaceSelfWithItsScopeClass(myTypeEvalContext.getType(qualifier)) ?: return
    val anchor = node.nameElement?.psi ?: node

    if (type is PyClassLikeType && !type.isAttributeWritable(attrName, myTypeEvalContext)) {
      registerProblem(anchor,
                      PyPsiBundle.message("INSP.unresolved.refs.class.object.has.no.attribute", type.name, attrName))
      return
    }

    if (type !is PyClassType) return
    if (PyUtil.isObjectClass(type.getPyClass())) return

    val isDefinition = type.isDefinition()
    val strictCheckEnabled = if (isDefinition) myStrictClassAttributes else myStrictInstanceAttributes
    if (!strictCheckEnabled) return

    if (!ContainerUtil.isEmpty(type.resolveMember(attrName, node as? PyExpression,
                                                  AccessDirection.READ, resolveContext))) return
    if (isDeclaredInSlots(type, attrName)) return
    // Instance-side: user-defined `__setattr__` accepts any name.
    if (!isDefinition && overridesSetAttr(type.getPyClass())) return
    val reference = node.getReference()
    if (reference != null && ignoreUnresolvedMemberForType(type, reference, attrName)) return

    registerProblem(anchor,
                    PyPsiBundle.message("INSP.unresolved.refs.unresolved.attribute.for.class", attrName, type.name),
                    ProblemHighlightType.WARNING)
  }

  private fun overridesSetAttr(cls: PyClass): Boolean {
    val setAttr = cls.findMethodByName("__setattr__", true, myTypeEvalContext)
    return setAttr != null && !getInstance(cls).isBuiltin(setAttr)
  }

  override fun visitPyElement(node: PyElement) {
    super.visitPyElement(node)
    if (node is PyReferenceOwner) {
      val resolveContext = PyResolveContext.defaultContext(myTypeEvalContext)
      processReference(node, node.getReference(resolveContext))
    }
    else {
      for (reference in node.getReferences()) {
        processReference(node, reference)
      }
    }
  }

  private fun processReference(node: PyElement, reference: PsiReference?) {
    if (!isEnabled(node) || reference == null || reference.isSoft()) {
      return
    }
    val guard: PyExceptPart? = getImportErrorGuard(node)
    if (guard != null) {
      return
    }
    if (node is PyQualifiedExpression) {
      val qualifier = node.getQualifier()
      val name = node.getName()
      if (qualifier != null && name != null && getNamesFromHasAttrs(node, qualifier).contains(name)) {
        return
      }
    }
    var target: PsiElement? = null
    val unresolved: Boolean
    if (reference is PsiPolyVariantReference) {
      val resolveResults = reference.multiResolve(false)
      unresolved = (resolveResults.size == 0)
      for (resolveResult in resolveResults) {
        if (target == null && resolveResult.isValidResult()) {
          target = resolveResult.getElement()
        }
      }
    }
    else {
      target = reference.resolve()
      unresolved = (target == null)
    }
    if (unresolved) {
      val ignoreUnresolved = ignoreUnresolved(node, reference) ||
                             node.getReachabilityForInspection(myTypeEvalContext) != Reachability.REACHABLE
      if (!ignoreUnresolved) {
        var severity = if (reference is PsiReferenceEx)
          reference.getUnresolvedHighlightSeverity(myTypeEvalContext)
        else
          HighlightSeverity.ERROR
        if (severity == null) {
          if (isAwaitCallToImportedNonAsyncFunction(reference)) {
            // special case: type of prefixExpression.getQualifier() is null but we want to check whether the called function is async
            severity = HighlightSeverity.WEAK_WARNING
          }
        }
        if (severity == null) return
        registerUnresolvedReferenceProblem(node, reference, severity)
      }
    }
    else if (reference is PyImportReference &&
             target is PsiFile && target === reference.element.containingFile &&
             !isContainingFileImportAllowed(node, target)) {
      registerProblem(node, PyPsiBundle.message("INSP.unresolved.refs.import.resolves.to.its.containing.file"))
    }
    else if (PyUnionType.isStrictSemanticsEnabled() && node is PyQualifiedExpression) {
      val referencedName = node.referencedName
      val qualifier: PyExpression? = if (node is PyCallSiteExpression && target is PyCallable) {
        node.getReceiver(target)
      }
      else {
        node.qualifier
      }
      if (referencedName != null && qualifier != null) {
        val qualifierType = myTypeEvalContext.getType(qualifier)
        if (qualifierType is PyUnionType) {
          val unionMemberMissingAttr = findStrictUnionMemberMissingAttribute(qualifierType, reference, referencedName)
          if (unionMemberMissingAttr != null) {
            val unionTypeRender = PythonDocumentationProvider.getTypeName(qualifierType, myTypeEvalContext)
            val unionMemberRender = PythonDocumentationProvider.getTypeName(unionMemberMissingAttr, myTypeEvalContext)
            registerProblem(
              node,
              PyPsiBundle.message(
                "INSP.unresolved.refs.unresolved.attribute.in.union.type", unionMemberRender, unionTypeRender,
                referencedName
              ),
              ProblemHighlightType.WEAK_WARNING,
              null,
              reference.getRangeInElement()
            )
          }
        }
      }
    }
  }

  private fun isAwaitCallToImportedNonAsyncFunction(reference: PsiReference): Boolean {
    val prefixExpression = reference.element as? PyPrefixExpression ?: return false
    if (PyNames.DUNDER_AWAIT != prefixExpression.operator.specialMethodName) return false
    val callExpression = getReferenceQualifier(reference) as? PyCallExpression ?: return false
    val callees = callExpression.multiResolveCalleeFunction(PyResolveContext.defaultContext(myTypeEvalContext))
    if (callees.isEmpty()) return false
    for (callee in callees) {
      if (callee is PyFunction && callee.isAsync) return false
    }
    return true // no signature is declared async -> warning
  }

  private fun registerUnresolvedReferenceProblem(node: PyElement, reference: PsiReference, severity: HighlightSeverity) {
    val spec = buildProblemSpec(node, reference, severity) ?: return
    val fixes = collectFixes(spec)
    emitProblem(spec, fixes)
  }

  private fun buildProblemSpec(initialNode: PyElement, reference: PsiReference, initialSeverity: HighlightSeverity): ProblemSpec? {
    if (reference is DocStringTypeReference) return null

    val element = reference.element
    val rangeInElement = reference.rangeInElement
    val text = element.text
    val refText = if (rangeInElement.startOffset >= 0 && rangeInElement.endOffset > 0) rangeInElement.substring(text) else text
    // Operator refs (e.g. `not x`) have null `referencedName`; do not fall back to refText, the empty-check below must fire.
    val refName: String? = if (element is PyQualifiedExpression) element.referencedName else refText
    if (refName.isNullOrEmpty()) return null

    val qualifiedNames = getCanonicalNames(reference, myTypeEvalContext)
    if (isIgnoredIdentifier(qualifiedNames, refName)) return null

    if (element is PyKeywordPattern) {
      val classPattern = element.containingClassPattern
      if (classPattern != null) {
        val classType = myTypeEvalContext.getType(classPattern) as? PyClassType
        if (classType != null && classType.getMemberNames(true, myTypeEvalContext).contains(refName)) return null
      }
    }

    var node: PyElement = initialNode
    var severity = initialSeverity
    var description: String? = null
    var resolvedRange = rangeInElement

    if (element is PyReferenceExpression) {
      when {
        PyNames.COMPARISON_OPERATORS.contains(refName) -> return null
        !element.isQualified && element.getReachabilityForInspection(myTypeEvalContext) != Reachability.REACHABLE -> return null
        PyModuleType.getPossibleInstanceMembers().contains(refName) -> return null

        PsiTreeUtil.getParentOfType(PsiTreeUtil.getParentOfType(node, PyImportElement::class.java),
                                    PyTryExceptStatement::class.java, PyIfStatement::class.java) != null -> {
          severity = HighlightSeverity.WEAK_WARNING
          description = PyPsiBundle.message("INSP.unresolved.refs.module.not.found", refText)
          // TODO: mark the node so that future references pointing to it won't result in a error, but in a warning
        }
      }
    }

    if (reference is PsiReferenceEx && description == null) {
      description = reference.unresolvedDescription
    }

    var qualifierType: PyType? = null
    var fallbackToUnqualifiedFix = false

    if (description == null) {
      if (element is PyQualifiedExpression) {
        // TODO: Add __qualname__ for Python 3.3 to the skeleton of <class 'object'>, introduce a pseudo-class skeleton for
        // <class 'function'>
        if ("__qualname__" == refText && !LanguageLevel.forElement(element).isPython2) return null
        if (PyNames.COMPARISON_OPERATORS.contains(refName)) return null
      }

      var markedQualified = false
      val qualifier = getReferenceQualifier(reference)
      if (qualifier != null) {
        val type = replaceSelfWithItsScopeClass(myTypeEvalContext.getType(qualifier))
        if (type != null) {
          if (ignoreUnresolvedMemberForType(type, reference, refName) || isDeclaredInSlots(type, refName)) return null
          qualifierType = type
          if (type is PyClassLikeType) {
            if (reference is PyOperatorReference) {
              var className = type.name
              if (type.isDefinition()) {
                className = type.getMetaClassType(myTypeEvalContext, true)?.name ?: className
              }
              description = PyPsiBundle.message("INSP.unresolved.refs.class.does.not.define.operator",
                                                className, refName, reference.readableOperatorName)
            }
            else {
              // TODO use proper type rendering here
              description = PyPsiBundle.message("INSP.unresolved.refs.unresolved.attribute.for.class", refText, type.name)
            }
          }
          else {
            val unionMemberWithoutAttr = findStrictUnionMemberMissingAttribute(type, reference, refName)
            if (unionMemberWithoutAttr != null) {
              val unionTypeRender = PythonDocumentationProvider.getTypeName(type, myTypeEvalContext)
              val unionMemberRender = PythonDocumentationProvider.getTypeName(unionMemberWithoutAttr, myTypeEvalContext)
              description = PyPsiBundle.message("INSP.unresolved.refs.unresolved.attribute.in.union.type",
                                                unionMemberRender, unionTypeRender, refName)
              severity = HighlightSeverity.WEAK_WARNING
            }
            else {
              description = PyPsiBundle.message("INSP.unresolved.refs.cannot.find.reference.in.type", refText, type.name)
            }
          }
          markedQualified = true
        }
        else {
          if (isAwaitCallToImportedNonAsyncFunction(reference)) {
            description = PyPsiBundle.message("INSP.await.call.on.imported.untyped.function", qualifier.text)
            node = qualifier // show warning on the function call
            resolvedRange = TextRange.create(0, qualifier.textRange.length)
            markedQualified = true
          }
          else if (reference is PyDunderMatchArgsReference) {
            markedQualified = true
          }
        }
      }

      if (!markedQualified) {
        description = PyPsiBundle.message("INSP.unresolved.refs.unresolved.reference", refText)
        fallbackToUnqualifiedFix = true
      }
    }

    val finalDescription = description ?: return null
    return ProblemSpec(node, element, reference, resolvedRange, refName, refText,
                       finalDescription, severity, qualifiedNames, qualifierType, fallbackToUnqualifiedFix)
  }

  private fun isIgnoredIdentifier(qualifiedNames: List<QualifiedName>, refName: String): Boolean {
    for (name in qualifiedNames) {
      val canonicalName = name.toString()
      myIgnoredIdentifiers.forEach { ignored ->
        if (ignored.endsWith(END_WILDCARD)) {
          val prefix = ignored.substring(0, ignored.length - END_WILDCARD.length)
          if (canonicalName.startsWith(prefix)) return true
        }
        else if (canonicalName == ignored) return true
      }
    }
    // Legacy non-qualified ignore patterns.
    return myIgnoredIdentifiers.contains(refName)
  }

  private fun collectFixes(spec: ProblemSpec): MutableList<LocalQuickFix> {
    val fixes: MutableList<LocalQuickFix> = ArrayList()
    val reference = spec.reference
    val element = spec.element

    when {
      reference is PsiReferenceEx -> {
        fixes.addAll(reference.getQuickFixes(myTypeEvalContext))
      }
      spec.qualifierType != null -> {
        fixes.addAll(getCreateMemberFromUsageFixes(myTypeEvalContext, spec.qualifierType, reference, spec.refText))
      }
      spec.fallbackToUnqualifiedFix -> {
        fixes.addAll(getAutoImportFixes(spec.node, reference, element))
        fixes.addIfNotNull(getCreateClassFix(myTypeEvalContext, spec.refText, element))
      }
      element is PyReferenceExpression && !element.isQualified -> {
        fixes.addIfNotNull(getTrueFalseQuickFix(spec.refText))
        fixes.addAll(getAddSelfFixes(myTypeEvalContext, spec.node, element))
        fixes.addIfNotNull(getCreateFunctionQuickFix(element))
        fixes.addIfNotNull(getAddParameterQuickFix(spec.refName, element))
        fixes.addIfNotNull(PyRenameUnresolvedRefQuickFix())
      }
    }
    return fixes
  }

  private fun emitProblem(spec: ProblemSpec, fixes: MutableList<LocalQuickFix>) {
    val hlType = computeHighlightType(spec.severity)

    ContainerUtil.addAll(fixes, getImportStatementQuickFixes(spec.element))
    ContainerUtil.addAll(fixes, getAddIgnoredIdentifierQuickFixes(spec.qualifiedNames))

    val installPackageQuickFixes = getInstallPackageQuickFixes(spec.node, spec.reference, spec.refName)
    val installAll = installPackageQuickFixes.isNotEmpty()
    if (installAll) {
      ContainerUtil.addAll(fixes, installPackageQuickFixes)
      myUnresolvedRefs.add(PyPackageInstallAllProblemInfo(spec.node, spec.description, hlType, spec.refName, fixes))
    }

    ContainerUtil.addIfNotNull(fixes, getAddSourceRootQuickFix(spec.node))

    // PySubstitutionChunkReference: install-all only, no direct registerProblem.
    if (spec.reference is PySubstitutionChunkReference) return

    getPluginQuickFixes(fixes, spec.reference)
    if (!installAll) {
      registerProblem(spec.node, spec.description, hlType, null, spec.rangeInElement, *fixes.toTypedArray())
    }
  }

  private fun computeHighlightType(severity: HighlightSeverity): ProblemHighlightType {
    if (myTypeEvalContext.usesExternalTypeEngine) return ProblemHighlightType.INFORMATION
    return when (severity) {
      HighlightSeverity.WARNING -> ProblemHighlightType.GENERIC_ERROR_OR_WARNING
      HighlightSeverity.WEAK_WARNING -> ProblemHighlightType.WEAK_WARNING
      HighlightSeverity.ERROR -> ProblemHighlightType.GENERIC_ERROR
      else -> ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
    }
  }

  private fun isDeclaredInSlots(type: PyType, attrName: String): Boolean {
    return type.toStream()
      .select(PyClassType::class.java)
      .map { obj: PyClassType? -> obj!!.getPyClass() }
      .flatMap { cls: PyClass? -> StreamEx.of<PyClass>(cls).append(cls!!.getAncestorClasses(myTypeEvalContext)) }
      .nonNull()
      .filter { c: PyClass? -> c!!.isNewStyleClass(myTypeEvalContext) }
      .flatCollection(Function { obj: PyClass? -> obj!!.getOwnSlots() })
      .anyMatch { it == attrName }
  }

  private fun ignoreUnresolvedMemberForType(type: PyType?, reference: PsiReference, name: String): Boolean {
    return when {
      type is PyTypeVarType -> isUnboundedTypeVar(type)
      type is PyUnionType -> isUnionMemberIgnored(type, reference, name)
      type is PyCompositeType -> type.members.any { ignoreUnresolvedMemberForType(it, reference, name) }

      // Unknown / Any-ish types: do not emit anything, we have no information to be confident.
      isUnknown(type, myTypeEvalContext) -> true

      type is PyStructuralType && type.isInferredFromUsages -> true
      type is PyImportedModuleType && type.importedModule.resolve() == null -> true
      type is PyCustomType && hasUnclearClassParent(type) -> true
      type is PyClassType && isDynamicClass(type, reference, name) -> true
      type is PyFunctionTypeImpl && hasUnknownAttrsDecorator(type) -> true
      type is PyModuleType && moduleDefinesGetAttr(type) -> true

      type != null -> isIgnoredByExtension(type, name)
      else -> false
    }
  }

  private fun isUnboundedTypeVar(type: PyTypeVarType): Boolean =
    type.bound == null && type.defaultType == null && type.constraints.isEmpty()

  /**
   * Under strict-union semantics a union is ignored only when every member resolves the attribute
   * — otherwise (e.g. `x: int | Any`) the missing-on-one-arm case is still reported. Under permissive
   * semantics, ignoring any one member is enough.
   */
  private fun isUnionMemberIgnored(type: PyUnionType, reference: PsiReference, name: String): Boolean {
    if (PyUnionType.isStrictSemanticsEnabled()) {
      return findStrictUnionMemberMissingAttribute(type, reference, name) == null
    }
    return type.members.any { ignoreUnresolvedMemberForType(it, reference, name) }
  }

  /** Custom type that mimics a class whose ancestors did not all resolve — too uncertain to flag. */
  private fun hasUnclearClassParent(type: PyCustomType): Boolean =
    type.typesToMimic
      .filterIsInstance<PyClassType>()
      .any { PyUtil.hasUnresolvedAncestors(it.pyClass, myTypeEvalContext) }

  /** Class participates in some dynamic-attribute mechanism (`__getattr__`, property, `@DynamicAttrs`, etc.). */
  private fun isDynamicClass(type: PyClassType, reference: PsiReference, name: String): Boolean {
    val cls = type.pyClass
    return overridesGetAttr(cls, myTypeEvalContext) ||
           cls.findProperty(name, true, myTypeEvalContext) != null ||
           PyUtil.hasUnresolvedAncestors(cls, myTypeEvalContext) ||
           isDecoratedAsDynamic(cls, true) ||
           hasUnresolvedDynamicMember(type, reference, name, myTypeEvalContext) ||
           isAwaitOnGeneratorBasedCoroutine(name, reference, cls)
  }

  private fun hasUnknownAttrsDecorator(type: PyFunctionTypeImpl): Boolean {
    val callable = type.callable
    return callable is PyFunction && PyKnownDecoratorUtil.hasUnknownOrUpdatingAttributesDecorator(callable, myTypeEvalContext)
  }

  /** Python 3.7+ module-level `__getattr__` defers unresolved attribute lookups to runtime. */
  private fun moduleDefinesGetAttr(type: PyModuleType): Boolean {
    val module = type.module
    return module.languageLevel.isAtLeast(LanguageLevel.PYTHON37) && definesGetAttr(module, myTypeEvalContext)
  }

  private fun isIgnoredByExtension(type: PyType, name: String): Boolean =
    PyInspectionExtension.EP_NAME.extensionList.any { it.ignoreUnresolvedMember(type, name, myTypeEvalContext) }

  private fun findStrictUnionMemberMissingAttribute(type: PyType, ref: PsiReference, name: String): PyType? {
    if (type !is PyUnionType || !PyUnionType.isStrictSemanticsEnabled()) {
      return null
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
    val location = ref.element as? PyExpression
    return type.members.firstOrNull { t ->
      t != null && !ignoreUnresolvedMemberForType(t, ref, name) &&
      ContainerUtil.isEmpty(t.resolveMember(name, location, AccessDirection.READ, resolveContext))
    }
  }

  private fun isDecoratedAsDynamic(cls: PyClass, inherited: Boolean): Boolean {
    if (inherited) {
      if (isDecoratedAsDynamic(cls, false)) {
        return true
      }
      for (base in cls.getAncestorClasses(myTypeEvalContext)) {
        if (base != null && isDecoratedAsDynamic(base, false)) {
          return true
        }
      }
    }
    else {
      val docString = cls.docStringValue
      if (docString != null && docString.contains("@DynamicAttrs")) {
        return true
      }
    }
    return false
  }

  private fun isAwaitOnGeneratorBasedCoroutine(name: String, reference: PsiReference, cls: PyClass): Boolean {
    if (PyNames.DUNDER_AWAIT == name &&
        reference is PyOperatorReference &&
        PyTypingTypeProvider.GENERATOR == cls.getQualifiedName()
    ) {
      val receiver = reference.getReceiver()

      if (receiver is PyCallExpression) {
        return PyKnownDecoratorUtil.isResolvedToGeneratorBasedCoroutine(receiver, resolveContext, myTypeEvalContext)
      }
    }

    return false
  }

  private fun isEnabled(anchor: PsiElement): Boolean {
    var enabled = myIsEnabled
    if (enabled == null) {
      enabled = overriddenUnresolvedReferenceInspection(anchor.containingFile) ?: true
      myIsEnabled = enabled
    }
    return enabled
  }

  fun addInstallAllImports() {
    val refNames = ContainerUtil.map2Set(
      myUnresolvedRefs,
      com.intellij.util.Function { it: PyPackageInstallAllProblemInfo? -> it!!.refName })
    val installAllPackageQuickFixes = getInstallAllPackagesQuickFix()
    for (unresolved in myUnresolvedRefs) {
      val quickFixes: MutableList<LocalQuickFix> = unresolved.fixes.toMutableList()
      if (refNames.size > 1) {
        ContainerUtil.addIfNotNull(quickFixes, installAllPackageQuickFixes)
      }
      registerProblem(
        unresolved.psiElement, unresolved.descriptionTemplate, unresolved.highlightType, null,
        *quickFixes.toTypedArray()
      )
    }
  }

  private fun ignoreUnresolved(node: PyElement, reference: PsiReference): Boolean {
    var ignoreUnresolved = false
    for (extension in PyInspectionExtension.EP_NAME.extensionList) {
      if (extension.ignoreUnresolvedReference(node, reference, myTypeEvalContext)) {
        ignoreUnresolved = true
        break
      }
    }
    return ignoreUnresolved
  }

  protected open fun getInstallPackageQuickFixes(
    node: PyElement,
    reference: PsiReference,
    refName: String,
  ): List<LocalQuickFix> = emptyList()

  protected open fun getInstallAllPackagesQuickFix(): LocalQuickFix? = null

  protected open fun getAddSourceRootQuickFix(node: PyElement): LocalQuickFix? {
    return null
  }

  protected open fun getAddIgnoredIdentifierQuickFixes(qualifiedNames: List<QualifiedName>): List<LocalQuickFix> = emptyList()

  protected open fun getImportStatementQuickFixes(element: PsiElement): List<LocalQuickFix> = emptyList()

  protected open fun getAutoImportFixes(node: PyElement, reference: PsiReference, element: PsiElement): List<LocalQuickFix> = emptyList()

  @VisibleForTesting
  open fun getPluginQuickFixes(fixes: MutableList<LocalQuickFix>, reference: PsiReference) {
    // Nothing.
  }

  private data class ProblemSpec(
    val node: PyElement,
    val element: PsiElement,
    val reference: PsiReference,
    val rangeInElement: TextRange,
    val refName: String,
    val refText: String,
    @InspectionMessage val description: String,
    val severity: HighlightSeverity,
    val qualifiedNames: List<QualifiedName>,
    val qualifierType: PyType?,
    val fallbackToUnqualifiedFix: Boolean,
  )

  companion object {
    private fun replaceSelfWithItsScopeClass(type: PyType?): PyType? {
      return if (type is PySelfType) type.scopeClassType else type
    }

    private fun overriddenUnresolvedReferenceInspection(file: PsiFile): Boolean? {
      return PyInspectionExtension.EP_NAME.extensionList
        .firstNotNullOfOrNull { it.overrideUnresolvedReferenceInspection(file) }
    }

    private fun getImportErrorGuard(node: PyElement?): PyExceptPart? {
      val importStatement = PsiTreeUtil.getParentOfType(node, PyImportStatementBase::class.java)
      if (importStatement != null) {
        val tryPart = PsiTreeUtil.getParentOfType(node, PyTryPart::class.java)
        if (tryPart != null) {
          val tryExceptStatement = PsiTreeUtil.getParentOfType(tryPart, PyTryExceptStatement::class.java)
          if (tryExceptStatement != null) {
            for (exceptPart in tryExceptStatement.getExceptParts()) {
              val expr = exceptPart.exceptClass
              if (expr != null && "ImportError" == expr.getName()) {
                return exceptPart
              }
            }
          }
        }
      }
      return null
    }

    private fun isContainingFileImportAllowed(node: PyElement?, target: PsiFile): Boolean {
      return PyImportStatementNavigator.getImportStatementByElement(node) == null && target.getName() == PyNames.INIT_DOT_PY
    }

    private fun getReferenceQualifier(reference: PsiReference): PyExpression? {
      val element = reference.getElement()

      if (element is PyQualifiedExpression) {
        val qualifier = element.getQualifier()
        if (qualifier != null) {
          return qualifier
        }
      }

      if (reference is PyFromImportNameReference) {
        val statement = PsiTreeUtil.getParentOfType(element, PyFromImportStatement::class.java)
        if (statement != null) {
          val source = statement.importSource
          if (source != null) {
            return source
          }
        }
      }

      return null
    }

    private fun hasUnresolvedDynamicMember(
      type: PyClassType,
      reference: PsiReference,
      name: String, typeEvalContext: TypeEvalContext,
    ): Boolean {
      val types: List<PyClassType> = listOf(type) + type.getAncestorTypes(typeEvalContext).filterIsInstance<PyClassType>()

      for (typeToCheck in types) {
        for (provider in PyClassMembersProvider.EP_NAME.extensionList) {
          val resolveResult = provider.getMembers(typeToCheck, reference.getElement(), typeEvalContext)
          for (member in resolveResult) {
            if (member.name == name) return true
          }
        }
      }

      return false
    }
  }
}
