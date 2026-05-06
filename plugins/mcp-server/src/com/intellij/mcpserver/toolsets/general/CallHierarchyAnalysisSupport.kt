package com.intellij.mcpserver.toolsets.general

import com.intellij.ide.actions.FqnUtil
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.hierarchy.CallHierarchyBrowserBase
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx
import com.intellij.ide.hierarchy.HierarchyBrowserScopes
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.hierarchy.LanguageCallHierarchy
import com.intellij.ide.hierarchy.ReferenceAwareNodeDescriptor
import com.intellij.ide.hierarchy.actions.BrowseHierarchyActionBase
import com.intellij.ide.util.gotoByName.ChooseByNameModel
import com.intellij.ide.util.gotoByName.DefaultChooseByNameItemProvider
import com.intellij.ide.util.gotoByName.GotoClassModel2
import com.intellij.ide.util.gotoByName.GotoSymbolModel2
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.Cancellation.checkCancelled
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiQualifiedNamedElement
import com.intellij.usageView.UsageViewUtil
import com.intellij.util.Processor
import com.intellij.util.asDisposable
import com.intellij.util.indexing.FindSymbolParameters
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.lang.reflect.Method
import java.util.ArrayDeque
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

private const val SYMBOL_SEARCH_LIMIT = 500
private const val OWNER_MEMBER_SCAN_LIMIT = 5000

internal suspend fun analyzeCalls(
  symbolFqn: String,
  analysisKind: AnalysisToolset.AnalysisKind,
  depth: Int,
  maxChildren: Int,
  maxNodes: Int,
  treePath: List<String>?,
  childOffset: Int,
  timeout: Int,
): String {
  val request = CallHierarchyRequest(
    symbolFqn = symbolFqn.trim(),
    analysisKind = analysisKind,
    depth = validateNonNegative(depth, CallHierarchyRequest::depth.name),
    maxChildren = validatePositive(maxChildren, CallHierarchyRequest::maxChildren.name),
    maxNodes = validatePositive(maxNodes, CallHierarchyRequest::maxNodes.name),
    treePath = treePath.orEmpty(),
    childOffset = validateNonNegative(childOffset, CallHierarchyRequest::childOffset.name),
    timeout = timeout,
  )
  if (request.symbolFqn.isBlank()) {
    mcpFail("${CallHierarchyRequest::symbolFqn.name} must not be blank")
  }

  val project = currentCoroutineContext().project
  return withTimeoutOrNull(request.timeout.milliseconds) {
    withBackgroundProgress(project, McpServerBundle.message("progress.title.analyzing.calls", request.symbolFqn), cancellable = true) {
      CallHierarchyAnalyzer(project, request).analyze()
    }
  } ?: "Timed out while analyzing calls for `${request.symbolFqn}`."
}

private fun validatePositive(value: Int, name: String): Int {
  if (value <= 0) {
    mcpFail("$name must be positive")
  }
  return value
}

private fun validateNonNegative(value: Int, name: String): Int {
  if (value < 0) {
    mcpFail("$name must not be negative")
  }
  return value
}

private data class CallHierarchyRequest(
  val symbolFqn: String,
  val analysisKind: AnalysisToolset.AnalysisKind,
  val depth: Int,
  val maxChildren: Int,
  val maxNodes: Int,
  val treePath: List<String>,
  val childOffset: Int,
  val timeout: Int,
)

private class CallHierarchyAnalyzer(
  private val project: Project,
  private val request: CallHierarchyRequest,
) {
  private val childrenCache = IdentityHashMap<HierarchyNodeDescriptor, List<CallNode>>()
  private var renderedNodes = 0

  suspend fun analyze(): String {
    checkCancelled()
    val rootSymbol = resolveTargetSymbol(request.symbolFqn)
    val hierarchy = createHierarchy(rootSymbol.element)
    val root = readAction { createCallNode(hierarchy.rootDescriptor, usageCount = 1) }
               ?: mcpFail("Call hierarchy target cannot be rendered for `${request.symbolFqn}`.")
    val subtreeRoot = resolveTreePath(hierarchy, root, request.treePath)
    val builder = StringBuilder()
    renderNode(
      hierarchy = hierarchy,
      node = subtreeRoot,
      treePath = request.treePath,
      prefix = "",
      isLast = true,
      remainingDepth = request.depth,
      childOffset = request.childOffset,
      builder = builder,
    )
    return builder.toString().trimEnd()
  }

  private suspend fun resolveTargetSymbol(symbolFqn: String): CallSymbol {
    val candidates = ArrayList<CallSymbol>()
    val exactSearchCandidates = ArrayList<CallSymbol>()
    for (targetElement in resolveTargetPsiElements(symbolFqn)) {
      checkCancelled()
      val candidate = readAction {
        resolveCallHierarchyTarget(targetElement.element)?.let(::createCallSymbol)
      } ?: continue
      if (candidate.matchesPlainFqn(symbolFqn)) {
        candidates.add(candidate)
      }
      else if (targetElement.matchedInputPattern) {
        exactSearchCandidates.add(candidate)
      }
    }

    val matchingCandidates = candidates.distinctBy { it.identityKey }
    val exactCandidates = exactSearchCandidates.distinctBy { it.identityKey }
    val resolvedCandidates = matchingCandidates.ifEmpty { exactCandidates }

    if (resolvedCandidates.isEmpty()) {
      mcpFail("No callable symbol found for ${CallHierarchyRequest::symbolFqn.name} `$symbolFqn`.\n" +
              "Pass a callable FQN such as `com.example.Service.run` or `com.example.Service.run(String)`.\n" +
              "Type roots are supported only when the language call hierarchy provider maps them to a callable target; " +
              "use `search_symbol` to find callable symbols when needed.")
    }
    if (resolvedCandidates.size > 1) {
      failAmbiguousSymbol(symbolFqn, resolvedCandidates)
    }
    return resolvedCandidates.single()
  }

  private suspend fun resolveTargetPsiElements(symbolFqn: String): List<TargetPsiElement> {
    val candidates = LinkedHashMap<PsiElement, TargetPsiElement>()
    fun addCandidates(elements: Iterable<PsiElement>, matchedInputPattern: Boolean) {
      for (element in elements) {
        candidates.compute(element) { _, existing ->
          existing?.copy(matchedInputPattern = existing.matchedInputPattern || matchedInputPattern)
          ?: TargetPsiElement(element, matchedInputPattern)
        }
      }
    }

    val nameWithoutParameters = symbolFqn.removeValueParameters()
    addCandidates(findQualifiedNameProviderCandidates(symbolFqn), matchedInputPattern = true)
    addCandidates(findSymbolCandidates(listOf(nameWithoutParameters)), matchedInputPattern = true)
    val shortPattern = nameWithoutParameters.substringAfterLast('.')
    if (shortPattern != nameWithoutParameters) {
      addCandidates(findSymbolCandidates(listOf(shortPattern)), matchedInputPattern = false)
    }
    val signature = parseCallableSignature(symbolFqn)
    if (signature != null) {
      addCandidates(findCallableCandidatesByOwner(signature), matchedInputPattern = true)
    }
    else {
      addCandidates(findTypeCandidates(symbolFqn), matchedInputPattern = true)
    }
    return candidates.values.toList()
  }

  private fun resolveCallHierarchyTarget(element: PsiElement): PsiElement? {
    val context = callHierarchyDataContext(element)
    val provider = BrowseHierarchyActionBase.findBestHierarchyProvider(LanguageCallHierarchy.INSTANCE, element, context) ?: return null
    return provider.getTarget(context)
  }

  private suspend fun createHierarchy(target: PsiElement): CallHierarchyTree = coroutineScope {
    val parentDisposable = asDisposable()
    readAction {
      val context = callHierarchyDataContext(target)
      val provider = BrowseHierarchyActionBase.findBestHierarchyProvider(LanguageCallHierarchy.INSTANCE, target, context)
                     ?: mcpFail("No call hierarchy provider found for `${request.symbolFqn}`.")
      val providerTarget = provider.getTarget(context)
                           ?: mcpFail("Call hierarchy provider cannot resolve target for `${request.symbolFqn}`.")
      val browser = provider.createHierarchyBrowser(providerTarget)
      if (browser is Disposable) {
        Disposer.register(parentDisposable, browser)
      }
      val browserEx = browser as? HierarchyBrowserBaseEx
                      ?: mcpFail("Call hierarchy provider for `${request.symbolFqn}` does not expose a hierarchy tree structure.")
      val type = when (request.analysisKind) {
        AnalysisToolset.AnalysisKind.INCOMING_CALLS -> CallHierarchyBrowserBase.getCallerType()
        AnalysisToolset.AnalysisKind.OUTGOING_CALLS -> CallHierarchyBrowserBase.getCalleeType()
      }
      val treeStructure = HierarchyBrowserTreeStructureFactory.createTreeStructure(
        browser = browserEx,
        type = type,
        target = providerTarget,
        scope = HierarchyBrowserScopes.SCOPE_ALL,
      ) ?: mcpFail("Call hierarchy provider cannot build a tree for `${request.symbolFqn}`.")
      val rootDescriptor = treeStructure.baseDescriptor
                           ?: mcpFail("Call hierarchy provider returned a tree without a root for `${request.symbolFqn}`.")
      CallHierarchyTree(treeStructure, rootDescriptor)
    }
  }

  private fun callHierarchyDataContext(element: PsiElement) = SimpleDataContext.builder()
    .add(CommonDataKeys.PROJECT, project)
    .add(CommonDataKeys.PSI_ELEMENT, element)
    .build()

  private fun failAmbiguousSymbol(symbolFqn: String, candidates: List<CallSymbol>): Nothing {
    val renderedCandidates = candidates.joinToString("\n") { candidate ->
      "- ${candidate.ambiguityDisplay}\n  ${CallHierarchyRequest::symbolFqn.name}: ${candidate.signature}"
    }
    mcpFail("Ambiguous ${CallHierarchyRequest::symbolFqn.name} `$symbolFqn`. " +
            "Pass one exact signature as `${CallHierarchyRequest::symbolFqn.name}`:\n" +
            renderedCandidates)
  }

  private suspend fun resolveTreePath(hierarchy: CallHierarchyTree, root: CallNode, treePath: List<String>): CallNode {
    var current = root
    val consumedPath = ArrayList<String>(treePath.size)
    for (component in treePath) {
      checkCancelled()
      val next = children(hierarchy, current).firstOrNull { it.symbol.signature == component }
                 ?: mcpFail("${CallHierarchyRequest::treePath.name} component not found under ${formatTreePath(consumedPath)}. Copy ${CallHierarchyRequest::treePath.name} values exactly from a previous ${AnalysisToolset::analyze_calls.name} result.")
      consumedPath.add(component)
      current = next
    }
    return current
  }

  private suspend fun renderNode(
    hierarchy: CallHierarchyTree,
    node: CallNode,
    treePath: List<String>,
    prefix: String,
    isLast: Boolean,
    remainingDepth: Int,
    childOffset: Int,
    builder: StringBuilder,
  ) {
    checkCancelled()
    renderedNodes++

    val connector = if (isLast) "└─" else "├─"
    val expandedChildren = if (remainingDepth > 0) children(hierarchy, node) else emptyList()
    val marker = if (expandedChildren.isNotEmpty() || remainingDepth == 0) "+ " else ""
    builder.append(prefix)
      .append(connector)
      .append(' ')
      .append(marker)
      .append(node.symbol.displayName)
      .append(' ')
      .append(formatNodeMetadata(node, treePath))
      .append('\n')

    if (remainingDepth == 0) {
      return
    }

    val childPrefix = prefix + if (isLast) "   " else "│  "
    if (childOffset > 0 && childOffset >= expandedChildren.size) {
      appendNoMoreChildrenLine(
        builder = builder,
        prefix = childPrefix,
        treePath = treePath,
        childOffset = childOffset,
        totalChildren = expandedChildren.size,
      )
      return
    }

    val childrenToRender = expandedChildren.drop(childOffset).take(request.maxChildren)
    val willRenderMoreLine = expandedChildren.size > childOffset + childrenToRender.size
    var renderedChildren = 0
    for (child in childrenToRender) {
      if (renderedNodes >= request.maxNodes) {
        appendMoreLine(
          builder = builder,
          prefix = childPrefix,
          remaining = expandedChildren.size - childOffset - renderedChildren,
          treePath = treePath,
          childOffset = childOffset + renderedChildren,
        )
        return
      }

      val childPath = treePath + child.symbol.signature
      val isLastRenderedChild = renderedChildren == childrenToRender.lastIndex && !willRenderMoreLine
      renderNode(
        hierarchy = hierarchy,
        node = child,
        treePath = childPath,
        prefix = childPrefix,
        isLast = isLastRenderedChild,
        remainingDepth = remainingDepth - 1,
        childOffset = 0,
        builder = builder,
      )
      renderedChildren++
    }

    val nextOffset = childOffset + renderedChildren
    val remaining = expandedChildren.size - nextOffset
    if (remaining > 0) {
      appendMoreLine(
        builder = builder,
        prefix = childPrefix,
        remaining = remaining,
        treePath = treePath,
        childOffset = nextOffset,
      )
    }
  }

  private fun appendMoreLine(
    builder: StringBuilder,
    prefix: String,
    remaining: Int,
    treePath: List<String>,
    childOffset: Int,
  ) {
    if (remaining <= 0) return
    builder.append(prefix)
      .append("└─")
      .append(" … and ")
      .append(remaining)
      .append(" more [")
      .append(CallHierarchyRequest::treePath.name)
      .append('=')
      .append(formatTreePath(treePath))
      .append(", ")
      .append(CallHierarchyRequest::childOffset.name)
      .append('=')
      .append(childOffset)
      .append("]\n")
  }

  private fun appendNoMoreChildrenLine(
    builder: StringBuilder,
    prefix: String,
    treePath: List<String>,
    childOffset: Int,
    totalChildren: Int,
  ) {
    builder.append(prefix)
      .append("└─")
      .append(" … no more children [")
      .append(CallHierarchyRequest::treePath.name)
      .append('=')
      .append(formatTreePath(treePath))
      .append(", ")
      .append(CallHierarchyRequest::childOffset.name)
      .append('=')
      .append(childOffset)
      .append(", totalChildren=")
      .append(totalChildren)
      .append("]\n")
  }

  private suspend fun children(hierarchy: CallHierarchyTree, node: CallNode): List<CallNode> {
    childrenCache[node.descriptor]?.let { return it }
    val children = hierarchyChildren(hierarchy, node.descriptor).sortedForRendering()
    childrenCache[node.descriptor] = children
    return children
  }

  private fun List<CallNode>.sortedForRendering(): List<CallNode> {
    return sortedWith(compareBy<CallNode> { it.symbol.displayName }.thenBy { it.symbol.signature })
  }

  private suspend fun hierarchyChildren(hierarchy: CallHierarchyTree, descriptor: HierarchyNodeDescriptor): List<CallNode> {
    return readAction {
      val result = LinkedHashMap<String, MutableCallNode>()
      for (child in hierarchy.treeStructure.getChildElements(descriptor)) {
        checkCancelled()
        val childDescriptor = child as? HierarchyNodeDescriptor ?: continue
        val childNode = createCallNode(childDescriptor, usageCount = usageCount(childDescriptor)) ?: continue
        result.addUsage(childNode)
      }
      result.values.map { it.toCallNode() }
    }
  }

  private fun createCallNode(descriptor: HierarchyNodeDescriptor, usageCount: Int): CallNode? {
    descriptor.update()
    val referenceAwareDescriptor = descriptor as? ReferenceAwareNodeDescriptor
    val element = referenceAwareDescriptor?.enclosingElement ?: descriptor.psiElement ?: return null
    val displayName = referenceAwareDescriptor?.getPresentation()?.singleLine()?.takeIf { it.isNotBlank() }
    val symbol = createCallSymbol(element, displayNameOverride = displayName, requireCallable = false) ?: return null
    return CallNode(symbol, usageCount, descriptor)
  }

  private fun usageCount(descriptor: HierarchyNodeDescriptor): Int {
    val references = (descriptor as? ReferenceAwareNodeDescriptor)?.references
    return references?.size?.takeIf { it > 0 } ?: 1
  }

  private fun createCallSymbol(element: PsiElement, displayNameOverride: String? = null, requireCallable: Boolean = true): CallSymbol? {
    val namedElement = canonicalNamedElement(element) ?: return null
    if (!isPresentableCallNode(namedElement)) return null
    val displayName = displayNameOverride ?: displayName(namedElement)
    val longName = safeUsageViewText(namedElement, UsageViewTextKind.LONG_NAME)
    if (requireCallable && !isCallableSymbol(namedElement, displayName, longName)) return null
    val signature = readableSignature(namedElement, displayName, longName)
    val fqnCandidates = searchableNames(namedElement, displayName, longName)
    return CallSymbol(
      element = namedElement,
      displayName = displayName,
      ambiguityDisplay = ambiguityDisplay(displayName, longName),
      signature = signature,
      fqnCandidates = fqnCandidates,
      filePath = symbolFilePath(namedElement),
    )
  }

  private suspend fun findSymbolCandidates(patterns: List<String>): List<PsiElement> {
    return findChooseByNameCandidates(patterns) { parentDisposable ->
      listOf(GotoClassModel2(project), GotoSymbolModel2(project, parentDisposable))
    }
  }

  private suspend fun findTypeCandidates(typeFqn: String): List<PsiElement> {
    val candidates = findChooseByNameCandidates(listOf(typeFqn)) { listOf(GotoClassModel2(project)) }
    return readAction {
      candidates.filter { candidate ->
        val namedElement = canonicalNamedElement(candidate) ?: return@filter false
        qualifiedName(namedElement) == typeFqn
      }
    }
  }

  private suspend fun findChooseByNameCandidates(
    patterns: List<String>,
    createModels: (Disposable) -> List<ChooseByNameModel>,
  ): List<PsiElement> = coroutineScope {
    val parentDisposable = asDisposable()
    val provider = DefaultChooseByNameItemProvider(null)
    val result = LinkedHashSet<PsiElement>()
    val uniquePatterns = patterns.distinct().filter { it.isNotBlank() }

    for (model in createModels(parentDisposable)) {
      val viewModel = McpChooseByNameViewModel(project, model, SYMBOL_SEARCH_LIMIT)
      for (pattern in uniquePatterns) {
        checkCancelled()
        val transformedPattern = viewModel.transformPattern(pattern)
        if (transformedPattern.isBlank()) continue
        val params = FindSymbolParameters.wrap(transformedPattern, project, true)
          .withLocalPattern(computeChooseByNameLocalPattern(model, transformedPattern))
        val remaining = SYMBOL_SEARCH_LIMIT - result.size
        val searchResult = coroutineToIndicator { indicator ->
          runBlockingCancellable {
            readAction {
              val elements = LinkedHashSet<PsiElement>()
              val completed = provider.filterElementsWithWeights(viewModel, params, indicator, Processor { descriptor: FoundItemDescriptor<*> ->
                indicator.checkCanceled()
                val element = descriptor.item.toPsiElement() ?: return@Processor true
                elements.add(element)
                elements.size < remaining
              })
              ChooseByNameSearchResult(elements.toList(), completed)
            }
          }
        }
        result.addAll(searchResult.elements)
        if (!searchResult.completed || result.size >= SYMBOL_SEARCH_LIMIT) return@coroutineScope result.toList()
      }
    }
    result.toList()
  }

  private suspend fun findQualifiedNameProviderCandidates(symbolFqn: String): List<PsiElement> {
    return readAction {
      qualifiedNameLookupVariants(symbolFqn).mapNotNull { qualifiedName ->
        runCatching { QualifiedNameProviderUtil.qualifiedNameToElement(qualifiedName, project) }.getOrNull()
      }
    }
  }

  private suspend fun findCallableCandidatesByOwner(signature: CallableSignatureParts): List<PsiElement> {
    val result = ArrayList<PsiElement>()
    for (owner in findTypeCandidates(signature.ownerFqn)) {
      result.addAll(readAction { namedDescendants(owner, signature.callableName) })
    }
    return result
  }

  private fun namedDescendants(root: PsiElement, name: String): List<PsiElement> {
    val result = ArrayList<PsiElement>()
    val stack = ArrayDeque<PsiElement>()
    stack.add(root)
    var visited = 0
    while (!stack.isEmpty() && visited < OWNER_MEMBER_SCAN_LIMIT) {
      checkCancelled()
      val element = stack.removeLast()
      visited++
      if (element !== root && element is PsiNamedElement && element.name == name) {
        result.add(element)
      }
      val children = safeChildren(element)
      for (index in children.indices.reversed()) {
        stack.add(children[index])
      }
    }
    return result
  }

  private fun safeChildren(element: PsiElement): Array<out PsiElement> {
    return runCatching { element.children }.getOrDefault(emptyArray())
  }

  private fun searchableNames(element: PsiNamedElement, displayName: String, longName: String): Set<String> {
    return buildSet {
      stableQualifiedNames(element).forEach(::add)
      element.name?.takeIf { it.isNotBlank() }?.let(::add)
      qualifiedName(element)?.takeIf { it.isNotBlank() }?.let(::add)
      parentQualifiedName(element)?.takeIf { it.isNotBlank() }?.let { parentName ->
        add(parentName)
        shortQualifiedSignature(parentName)?.let(::add)
      }
      displayName.takeIf { it.isNotBlank() }?.let(::add)
      longName.takeIf { it.isNotBlank() }?.let(::add)
      val name = element.name
      val parentName = parentQualifiedName(element)
      if (!name.isNullOrBlank() && !parentName.isNullOrBlank()) {
        add("$parentName.$name")
      }
    }
  }

  private fun stableQualifiedNames(element: PsiNamedElement): Set<String> {
    return buildSet {
      fun addQualifiedName(candidate: PsiElement?) {
        if (candidate == null || !candidate.isValid) return
        (candidate as? PsiQualifiedNamedElement)?.qualifiedName?.takeIf { it.isNotBlank() }?.let(::add)
        runCatching { FqnUtil.getQualifiedNameFromProviders(candidate) }.getOrNull()?.takeIf { it.isNotBlank() }?.let(::add)
        runCatching { QualifiedNameProviderUtil.getQualifiedName(candidate) }.getOrNull()?.takeIf { it.isNotBlank() }?.let(::add)
      }

      addQualifiedName(element)
      addQualifiedName(runCatching { QualifiedNameProviderUtil.adjustElementToCopy(element) }.getOrNull())
      val navigationElement = runCatching { element.navigationElement }.getOrNull()
      if (navigationElement !== element) {
        addQualifiedName(navigationElement)
        navigationElement?.let { addQualifiedName(runCatching { QualifiedNameProviderUtil.adjustElementToCopy(it) }.getOrNull()) }
      }
    }
  }

  private fun parentQualifiedName(element: PsiNamedElement): String? {
    val name = element.name ?: return null
    var current = element.parent
    while (current != null && current !is PsiFile) {
      val parentName = canonicalNamedElement(current)?.let(::qualifiedName)
      if (!parentName.isNullOrBlank()) {
        return if (parentName.endsWith(".$name")) parentName else "$parentName.$name"
      }
      current = current.parent
    }
    val longName = safeUsageViewText(element, UsageViewTextKind.LONG_NAME)
    return longName.takeIf { it.endsWith(".$name") || it.endsWith("#$name") || it.endsWith("::$name") }
  }

  private fun qualifiedName(element: PsiNamedElement): String? {
    (element as? PsiQualifiedNamedElement)?.qualifiedName?.let { qualifiedName ->
      if (qualifiedName.isNotBlank()) return qualifiedName
    }
    val longName = safeUsageViewText(element, UsageViewTextKind.LONG_NAME)
    return longName.takeIf { it.isNotBlank() }
  }

  private fun readableSignature(element: PsiNamedElement, displayName: String, longName: String): String {
    val name = element.name.orEmpty()
    val parameters = valueParameters(longName, name) ?: valueParameters(displayName, name).orEmpty()
    val baseName = parentQualifiedName(element)
                   ?: qualifiedName(element)?.removeValueParameters()
                   ?: name
    return (baseName + parameters).singleLine()
  }

  private fun formatNodeMetadata(node: CallNode, treePath: List<String>): String {
    return buildList {
      if (node.usageCount > 1) add("${node.usageCount} usages")
      node.symbol.filePath?.let { add("filePath=${Json.encodeToString(it)}") }
      add("${CallHierarchyRequest::treePath.name}=${formatTreePath(treePath)}")
    }.joinToString(prefix = "[", postfix = "]")
  }

  private fun symbolFilePath(element: PsiNamedElement): String? {
    val navigationElement = element.navigationElement ?: element
    val file = runCatching { navigationElement.containingFile?.virtualFile }.getOrNull()
               ?: runCatching { element.containingFile?.virtualFile }.getOrNull()
               ?: return null
    if (!file.isValid) return null
    return project.projectDirectory.relativizeIfPossible(file).takeIf { it.isNotBlank() }
  }
}

private data class CallSymbol(
  val element: PsiNamedElement,
  val displayName: String,
  val ambiguityDisplay: String,
  val signature: String,
  val fqnCandidates: Set<String>,
  val filePath: String?,
) {
  val identityKey: String = fqnCandidates
    .asSequence()
    .flatMap { comparableNameVariants(it).asSequence() }
    .firstOrNull()
                           ?: listOfNotNull(filePath, signature).joinToString(":")

  fun matchesPlainFqn(symbolFqn: String): Boolean {
    val includeParameterlessNames = !symbolFqn.contains('(')
    val requestedNames = comparableNameVariants(symbolFqn, includeParameterlessNames)
    return comparableNameVariants(signature, includeParameterlessNames).any { it in requestedNames } ||
           fqnCandidates.any { candidate -> comparableNameVariants(candidate, includeParameterlessNames).any { it in requestedNames } }
  }
}

private data class TargetPsiElement(
  val element: PsiElement,
  val matchedInputPattern: Boolean,
)

private data class CallNode(
  val symbol: CallSymbol,
  val usageCount: Int,
  val descriptor: HierarchyNodeDescriptor,
)

private data class CallHierarchyTree(
  val treeStructure: HierarchyTreeStructure,
  val rootDescriptor: HierarchyNodeDescriptor,
)

private data class ChooseByNameSearchResult(
  val elements: List<PsiElement>,
  val completed: Boolean,
)

private data class CallableSignatureParts(
  val ownerFqn: String,
  val callableName: String,
)

private class MutableCallNode(
  private val node: CallNode,
) {
  private var usageCount = 0

  fun addUsage(count: Int) {
    usageCount += count
  }

  fun toCallNode(): CallNode = node.copy(usageCount = usageCount)
}

private fun MutableMap<String, MutableCallNode>.addUsage(node: CallNode) {
  val mutableNode = getOrPut(node.symbol.signature) { MutableCallNode(node) }
  mutableNode.addUsage(node.usageCount)
}

private fun canonicalNamedElement(element: PsiElement): PsiNamedElement? {
  if (element is PsiNamedElement && hasCallablePresentation(element)) return element
  val navigationElement = element.navigationElement ?: element
  if (navigationElement is PsiNamedElement) return navigationElement
  if (element is PsiNamedElement) return element
  var current: PsiElement? = element.parent
  while (current != null && current !is PsiFile) {
    if (current is PsiNamedElement) return current
    current = current.parent
  }
  return null
}

private fun isPresentableCallNode(element: PsiNamedElement): Boolean {
  if (!element.isValid || element is PsiFile) return false
  if (runCatching { element.containingFile }.getOrNull() == null) return false
  if (element.name.isNullOrBlank()) return false
  return displayName(element).isNotBlank()
}

private fun isCallableSymbol(element: PsiNamedElement, displayName: String, longName: String): Boolean {
  val name = element.name ?: return false
  return valueParameters(longName, name) != null || valueParameters(displayName, name) != null
}

private fun hasCallablePresentation(element: PsiNamedElement): Boolean {
  val name = element.name ?: return false
  return valueParameters(safeUsageViewText(element, UsageViewTextKind.LONG_NAME), name) != null ||
         valueParameters(safeUsageViewText(element, UsageViewTextKind.NODE_TEXT), name) != null
}

private fun displayName(element: PsiNamedElement): String {
  return safeUsageViewText(element, UsageViewTextKind.NODE_TEXT)
    .ifBlank { safeUsageViewText(element, UsageViewTextKind.LONG_NAME) }
    .ifBlank { element.name.orEmpty() }
    .singleLine()
}

private fun ambiguityDisplay(displayName: String, longName: String): String {
  val normalizedLongName = longName.singleLine()
  if (normalizedLongName.isBlank() || normalizedLongName == displayName) {
    return displayName
  }
  return "$displayName ($normalizedLongName)"
}

private enum class UsageViewTextKind {
  NODE_TEXT,
  LONG_NAME,
}

private fun safeUsageViewText(element: PsiElement, kind: UsageViewTextKind): String {
  return runCatching {
    when (kind) {
      UsageViewTextKind.NODE_TEXT -> UsageViewUtil.createNodeText(element)
      UsageViewTextKind.LONG_NAME -> UsageViewUtil.getLongName(element)
    }
  }.getOrDefault("").singleLine()
}

private fun String.singleLine(): String = replace('\n', ' ').replace('\r', ' ').trim()

private fun valueParameters(text: String, name: String): String? {
  val parameterStart = text.indexOf("$name(").takeIf { it >= 0 }?.let { it + name.length } ?: return null
  var balance = 0
  for (index in parameterStart until text.length) {
    when (text[index]) {
      '(' -> balance++
      ')' -> {
        balance--
        if (balance == 0) {
          return text.substring(parameterStart, index + 1).singleLine()
        }
      }
    }
  }
  return null
}

private fun String.removeValueParameters(): String {
  val parameterStart = indexOf('(')
  return if (parameterStart == -1) this else substring(0, parameterStart)
}

private fun qualifiedNameLookupVariants(symbolFqn: String): Set<String> {
  val name = symbolFqn.singleLine()
  if (name.isBlank()) return emptySet()
  return buildSet {
    add(name)
    add(name.removeValueParameters())
    memberSeparatorVariants(name).forEach { variant ->
      add(variant)
      add(variant.removeValueParameters())
    }
  }
}

private fun comparableNameVariants(name: String): Set<String> {
  return comparableNameVariants(name, includeParameterlessName = true)
}

private fun comparableNameVariants(name: String, includeParameterlessName: Boolean): Set<String> {
  val singleLineName = name.singleLine()
  if (singleLineName.isBlank()) return emptySet()
  return buildSet {
    add(singleLineName)
    if (includeParameterlessName) {
      add(singleLineName.removeValueParameters())
    }
    val dotSeparatedName = singleLineName.replace('#', '.')
    add(dotSeparatedName)
    if (includeParameterlessName) {
      add(dotSeparatedName.removeValueParameters())
    }
    shortQualifiedSignature(dotSeparatedName)?.let { shortName ->
      add(shortName)
      if (includeParameterlessName) {
        add(shortName.removeValueParameters())
      }
    }
  }
}

private fun memberSeparatorVariants(name: String): Set<String> {
  return buildSet {
    add(name)
    if ('#' in name) {
      add(name.replace('#', '.'))
    }
    val withoutParameters = name.removeValueParameters()
    val lastDot = withoutParameters.lastIndexOf('.')
    if (lastDot > 0 && lastDot < withoutParameters.lastIndex) {
      add(withoutParameters.substring(0, lastDot) + '#' + name.substring(lastDot + 1))
    }
  }
}

private fun parseCallableSignature(signature: String): CallableSignatureParts? {
  if (!signature.contains('(')) return null
  val withoutParameters = signature.removeValueParameters()
  val separator = withoutParameters.lastIndexOf('.')
  if (separator <= 0 || separator == withoutParameters.lastIndex) return null
  return CallableSignatureParts(
    ownerFqn = withoutParameters.substring(0, separator),
    callableName = withoutParameters.substring(separator + 1),
  )
}

private fun shortQualifiedSignature(signature: String): String? {
  val withoutParameters = signature.removeValueParameters()
  val lastDot = withoutParameters.lastIndexOf('.')
  if (lastDot <= 0) return null
  val ownerDot = withoutParameters.lastIndexOf('.', startIndex = lastDot - 1)
  val shortName = withoutParameters.substring(ownerDot + 1)
  return shortName + signature.substring(withoutParameters.length)
}

private fun Any?.toPsiElement(): PsiElement? {
  return when (this) {
    is PsiElement -> this
    is com.intellij.navigation.PsiElementNavigationItem -> targetElement
    else -> null
  }
}

private fun formatTreePath(treePath: List<String>): String = Json.encodeToString(treePath)

private object HierarchyBrowserTreeStructureFactory {
  private val createHierarchyTreeStructureMethod: Method by lazy {
    HierarchyBrowserBaseEx::class.java.getDeclaredMethod("createHierarchyTreeStructure", String::class.java, PsiElement::class.java).apply {
      isAccessible = true
    }
  }

  private val typeToSheetField by lazy {
    HierarchyBrowserBaseEx::class.java.getDeclaredField("myType2Sheet").apply { isAccessible = true }
  }

  private val currentSheetField by lazy {
    HierarchyBrowserBaseEx::class.java.getDeclaredField("myCurrentSheet").apply { isAccessible = true }
  }

  fun createTreeStructure(browser: HierarchyBrowserBaseEx, type: String, target: PsiElement, scope: String): HierarchyTreeStructure? {
    installScope(browser, type, scope)
    return createHierarchyTreeStructureMethod.invoke(browser, type, target) as? HierarchyTreeStructure
  }

  private fun installScope(browser: HierarchyBrowserBaseEx, type: String, scope: String) {
    @Suppress("UNCHECKED_CAST")
    val typeToSheet = typeToSheetField.get(browser) as? Map<String, Any> ?: return
    val sheet = typeToSheet[type] ?: return
    val scopeField = sheet.javaClass.getDeclaredField("myScope").apply { isAccessible = true }
    scopeField.set(sheet, scope)

    @Suppress("UNCHECKED_CAST")
    val currentSheet = currentSheetField.get(browser) as? AtomicReference<Any> ?: return
    currentSheet.set(sheet)
  }
}
