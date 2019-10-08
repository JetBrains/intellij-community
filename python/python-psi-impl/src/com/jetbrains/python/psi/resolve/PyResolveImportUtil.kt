// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

@file:JvmName("PyResolveImportUtil")

package com.jetbrains.python.psi.resolve

import com.google.common.base.Preconditions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.util.QualifiedName
import com.intellij.util.Consumer
import com.jetbrains.python.codeInsight.typing.PyTypeShed
import com.jetbrains.python.codeInsight.typing.isInInlinePackage
import com.jetbrains.python.codeInsight.typing.isInStubPackage
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil
import com.jetbrains.python.facet.PythonPathContributingFacet
import com.jetbrains.python.module.PyModuleService
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyImportResolver
import com.jetbrains.python.pyi.PyiFile
import com.jetbrains.python.pyi.PyiUtil
import com.jetbrains.python.sdk.PythonSdkUtil
import java.util.regex.Pattern

/**
 * Python resolve utilities for qualified names.
 *
 * TODO: Merge with ResolveImportUtil, maybe make these functions the methods of PyQualifiedNameResolveContext.
 *
 * @author vlan
 */


/**
 * Resolves a qualified [name] to the list of packages and modules according to the [context].
 *
 * @see resolveTopLevelMember
 */
fun resolveQualifiedName(name: QualifiedName, context: PyQualifiedNameResolveContext): List<PsiElement> {
  checkAccess()
  if (!context.isValid) {
    return emptyList()
  }

  val relativeDirectory = context.containingDirectory
  val relativeResults = resolveWithRelativeLevel(name, context)
  val foundRelativeImport = relativeDirectory != null &&
                            relativeResults.any { isRelativeImportResult(name, relativeDirectory, it, context) }

  val cache = findCache(context)
  val mayCache = cache != null && !foundRelativeImport
  val key = cachePrefix(context).append(name)

  if (mayCache) {
    val cachedResults = cache?.get(key)
    if (cachedResults != null) {
      return relativeResults + cachedResults
    }
  }

  val foreignResults = foreignResults(name, context)
  val pythonResults = listOf(relativeResults,
                             resolveModuleFromRoots(name, context),
                             relativeResultsFromSkeletons(name, context)).flatten().distinct()
  val allResults = pythonResults + foreignResults
  val results = if (name.componentCount > 0) filterTopPriorityResults(pythonResults, context.module) + foreignResults else allResults

  if (mayCache) {
    cache?.put(key, results)
  }

  return results
}

/**
 * Resolves a qualified [name] to the list of packages and modules with Python semantics according to the [context].
 */
private fun resolveModuleFromRoots(name: QualifiedName, context: PyQualifiedNameResolveContext): List<PsiElement> {
  val head = name.removeTail(name.componentCount - 1)
  val nameNoHead = name.removeHead(1)
  return nameNoHead.components.fold(resultsFromRoots(head, context).distinct()) { results, component ->
    filterTopPriorityResults(results, context.module)
      .asSequence()
      .filterIsInstance<PsiFileSystemItem>()
      .flatMap { resolveModuleAt(QualifiedName.fromComponents(component), it, context).asSequence() }
      .toList()
  }
}


/**
 * Resolves a [name] to the first module member defined at the top-level.
 */
fun resolveTopLevelMember(name: QualifiedName, context: PyQualifiedNameResolveContext): PsiElement? {
  checkAccess()
  val memberName = name.lastComponent ?: return null
  return resolveQualifiedName(name.removeLastComponent(), context)
    .asSequence()
    .filterIsInstance(PyFile::class.java)
    .flatMap { it.multiResolveName(memberName).asSequence() }
    .map { it.element }
    .firstOrNull()
}

/**
 * Resolves a [name] relative to the specified [item].
 */
fun resolveModuleAt(name: QualifiedName, item: PsiFileSystemItem?, context: PyQualifiedNameResolveContext): List<PsiElement> {
  checkAccess()
  val empty = emptyList<PsiElement>()
  if (item == null || !item.isValid) {
    return empty
  }
  return name.components.fold(listOf<PsiElement>(item)) { seekers, component ->
    if (component == null) empty
    else seekers.flatMap {
      val children = ResolveImportUtil.resolveChildren(it, component, context.footholdFile,
                                                       !context.withMembers,
                                                       !context.withPlainDirectories, context.withoutStubs,
                                                       context.withoutForeign)
      PyUtil.filterTopPriorityResults(children.toTypedArray())
    }
  }
}

/**
 * Creates a [PyQualifiedNameResolveContext] from a [foothold] element.
 */
fun fromFoothold(foothold: PsiElement): PyQualifiedNameResolveContext {
  val module = ModuleUtilCore.findModuleForPsiElement(foothold.containingFile ?: foothold)
  return PyQualifiedNameResolveContextImpl(foothold.manager, module, foothold,
                                           PythonSdkUtil.findPythonSdk(module))
}

/**
 * Creates a [PyQualifiedNameResolveContext] from a [module].
 */
fun fromModule(module: Module): PyQualifiedNameResolveContext =
  PyQualifiedNameResolveContextImpl(PsiManager.getInstance(module.project), module, null,
                                    PythonSdkUtil.findPythonSdk(module))

/**
 * Creates a [PyQualifiedNameResolveContext] from an [sdk].
 */
fun fromSdk(project: Project, sdk: Sdk): PyQualifiedNameResolveContext =
  PyQualifiedNameResolveContextImpl(PsiManager.getInstance(project), module = null, foothold = null,
                                    sdk = sdk)

private fun cachePrefix(context: PyQualifiedNameResolveContext): QualifiedName {
  val results = mutableListOf<String>()
  if (context.withoutStubs) {
    results.add("without-stubs")
  }
  if (context.withoutForeign) {
    results.add("without-foreign")
  }
  if (context.withoutRoots) {
    results.add("without-roots")
  }
  return QualifiedName.fromComponents(results)
}

private fun foreignResults(name: QualifiedName, context: PyQualifiedNameResolveContext) =
  if (context.withoutForeign)
    emptyList()
  else
    PyImportResolver.EP_NAME.extensionList
      .asSequence()
      .map { it.resolveImportReference(name, context, !context.withoutRoots) }
      .filterNotNull()
      .toList()

private fun relativeResultsFromSkeletons(name: QualifiedName, context: PyQualifiedNameResolveContext): List<PsiElement> {
  val footholdFile = context.footholdFile
  if (context.withoutRoots && footholdFile != null) {
    val virtualFile = footholdFile.virtualFile
    if (virtualFile == null || FileIndexFacade.getInstance(context.project).isInContent(virtualFile)) {
      return emptyList()
    }
    val containingDirectory = context.containingDirectory
    if (containingDirectory != null) {
      val containingName = QualifiedNameFinder.findCanonicalImportPath(containingDirectory, null)
      if (containingName != null && containingName.componentCount > 0) {
        val absoluteName = containingName.append(name)
        val sdk = PythonSdkUtil.findPythonSdk(footholdFile) ?: return emptyList()
        val skeletonsVirtualFile = PythonSdkUtil.findSkeletonsDir(sdk) ?: return emptyList()
        val skeletonsDir = context.psiManager.findDirectory(skeletonsVirtualFile)
        return resolveModuleAt(absoluteName, skeletonsDir, context.copyWithoutForeign())
      }
    }
  }
  return emptyList()
}

fun relativeResultsForStubsFromRoots(name: QualifiedName, context: PyQualifiedNameResolveContext): List<PsiElement> {
  if (context.footholdFile !is PyiFile || context.relativeLevel <= 0) {
    return emptyList()
  }
  val containingDirectory = context.containingDirectory ?: return emptyList()
  val containingName = QualifiedNameFinder.findCanonicalImportPath(containingDirectory, null) ?: return emptyList()
  if (containingName.componentCount <= 0) {
    return emptyList()
  }
  val absoluteName = containingName.append(name)
  return resultsFromRoots(absoluteName, context.copyWithRelative(-1).copyWithRoots())
}

private fun resolveWithRelativeLevel(name: QualifiedName, context: PyQualifiedNameResolveContext): List<PsiElement> {
  val footholdFile = context.footholdFile
  if (context.relativeLevel >= 0 && footholdFile != null && !PyUserSkeletonsUtil.isUnderUserSkeletonsDirectory(footholdFile)) {
    return resolveModuleAt(name, context.containingDirectory,
                           context) + relativeResultsForStubsFromRoots(
      name, context)
  }
  return emptyList()
}

/**
 * Collects resolve results from all roots available.
 *
 * The retuning {@code List<PsiElement>} contains all elements {@code name} references
 * and does not take into account root order.
 */
private fun resultsFromRoots(name: QualifiedName, context: PyQualifiedNameResolveContext): List<PsiElement> {
  if (context.withoutRoots) {
    return emptyList()
  }

  val moduleResults = mutableListOf<PsiElement>()
  val sdkResults = mutableListOf<PsiElement>()

  val sdk = context.effectiveSdk
  val module = context.module
  val footholdFile = context.footholdFile
  val withoutStubs = context.withoutStubs

  val visitor = RootVisitor { root, module, sdk, isModuleSource ->
    val results = if (isModuleSource) moduleResults else sdkResults
    val effectiveSdk = sdk ?: context.effectiveSdk
    if (!root.isValid ||
        root == PyUserSkeletonsUtil.getUserSkeletonsDirectory() ||
        effectiveSdk != null && PyTypeShed.isInside(root) && !PyTypeShed.maySearchForStubInRoot(name, root, effectiveSdk)) {
      return@RootVisitor true
    }
    if (withoutStubs && (PyTypeShed.isInside(root) ||
                         PsiManager.getInstance(context.project).findDirectory(root)?.let { isInStubPackage(it) } == true)) {
      return@RootVisitor true
    }
    results.addAll(resolveInRoot(name, root, context))
    if (isAcceptRootAsTopLevelPackage(context) && name.matchesPrefix(
        QualifiedName.fromDottedString(root.name))) {
      //TODO[akniazev]: resolving from parent root. Is it still a thing for Django?
      results.addAll(resolveInRoot(name, root.parent, context))
    }
    return@RootVisitor true
  }

  when {
    context.visitAllModules -> {
      ModuleManager.getInstance(context.project).modules.forEach {
        RootVisitorHost.visitRoots(it, true, visitor)
      }
      when {
        sdk != null ->
          RootVisitorHost.visitSdkRoots(sdk, visitor)
        footholdFile != null ->
          RootVisitorHost.visitSdkRoots(footholdFile, visitor)
      }
    }
    module != null -> {
      val otherSdk = sdk != context.sdk
      RootVisitorHost.visitRoots(module, otherSdk, visitor)
      if (otherSdk && sdk != null) {
        RootVisitorHost.visitSdkRoots(sdk, visitor)
      }
    }
    footholdFile != null -> {
      RootVisitorHost.visitRoots(footholdFile, visitor)
    }
    sdk != null -> {
      RootVisitorHost.visitSdkRoots(sdk, visitor)
    }
    else -> throw IllegalStateException()
  }
  return moduleResults + sdkResults
}

private fun isAcceptRootAsTopLevelPackage(context: PyQualifiedNameResolveContext): Boolean {
  context.module?.let { it ->
    val ref = Ref.create(false)
    PyModuleService.getInstance().forAllFacets(it) {
      if (it is PythonPathContributingFacet && it.acceptRootAsTopLevelPackage()) {
        ref.set(true)
      }
    }
    return ref.get()
  }
  return false
}

private fun resolveInRoot(name: QualifiedName, root: VirtualFile, context: PyQualifiedNameResolveContext): List<PsiElement> {
  return if (root.isDirectory) resolveModuleAt(name, context.psiManager.findDirectory(root), context) else emptyList()
}

private fun findCache(context: PyQualifiedNameResolveContext): PythonPathCache? {
  return when {
    context.visitAllModules -> null
    context.module != null ->
      if (context.effectiveSdk != context.sdk) null else PythonModulePathCache.getInstance(context.module)
    context.footholdFile != null -> {
      val sdk = PyBuiltinCache.findSdkForNonModuleFile(context.footholdFile!!)
      if (sdk != null) PythonSdkPathCache.getInstance(context.project, sdk) else null
    }
    else -> null
  }
}

private fun isRelativeImportResult(name: QualifiedName, directory: PsiDirectory, result: PsiElement,
                                   context: PyQualifiedNameResolveContext): Boolean {
  if (context.relativeLevel > 0) {
    return true
  }
  else {
    val py2 = LanguageLevel.forElement(directory).isPython2
    return context.relativeLevel == 0 && py2 && PyUtil.isPackage(directory, false, null) &&
           result is PsiFileSystemItem && name != QualifiedNameFinder.findShortestImportableQName(result)
  }
}

private fun checkAccess() {
  Preconditions.checkState(ApplicationManager.getApplication().isReadAccessAllowed, "This method requires read access")
}

/**
 * Filters resolved elements according to their import priority in sys.path and
 * [PEP 561](https://www.python.org/dev/peps/pep-0561/#type-checker-module-resolution-order) rules.
 */
private fun filterTopPriorityResults(resolved: List<PsiElement>, module: Module?): List<PsiElement> {
  if (resolved.isEmpty()) return emptyList()

  val groupedResults = resolved.groupByTo(sortedMapOf<Priority, MutableList<PsiElement>>()) { resolvedElementPriority(it, module) }

  if (groupedResults.containsKey(Priority.NAMESPACE_PACKAGE) &&
      groupedResults.headMap(Priority.NAMESPACE_PACKAGE).isEmpty()) return groupedResults[Priority.NAMESPACE_PACKAGE]!!

  groupedResults.remove(Priority.NAMESPACE_PACKAGE)

  return if (groupedResults.containsKey(Priority.STUB_PACKAGE) && groupedResults.headMap(Priority.STUB_PACKAGE).isEmpty()) {
    // stub packages + next by priority
    // because stub packages could be partial

    val stub = groupedResults[Priority.STUB_PACKAGE]!!.first()
    val nextByPriority = groupedResults.tailMap(Priority.STUB_PACKAGE).values.asSequence().drop(1).take(1).flatten().firstOrNull()

    listOfNotNull(stub, nextByPriority)
  }
  else {
    listOf(groupedResults.values.first().first())
  }
}

/**
 * See [https://www.python.org/dev/peps/pep-0561/#type-checker-module-resolution-order].
 */
private fun resolvedElementPriority(element: PsiElement, module: Module?) = when {
  isNamespacePackage(element) -> Priority.NAMESPACE_PACKAGE
  isUserFile(element, module) -> if (PyiUtil.isPyiFileOfPackage(element)) Priority.USER_STUB else Priority.USER_CODE
  isInStubPackage(element) -> Priority.STUB_PACKAGE
  isInTypeShed(element) -> Priority.TYPESHED
  PyiUtil.isPyiFileOfPackage(element) -> Priority.PROVIDED_STUB
  isInInlinePackage(element, module) -> Priority.INLINE_PACKAGE
  else -> Priority.OTHER
}

private fun isNamespacePackage(element: PsiElement): Boolean {
  if (element is PsiDirectory) {
    val level = PyUtil.getLanguageLevelForVirtualFile(element.project, element.virtualFile)
    val initFile = PyUtil.turnDirIntoInit(element) ?: return !level.isPython2
    val initText = initFile.text ?: return false
    return isNamespaceDeclaration(initText, pkgutilsInitPatterns) || isNamespaceDeclaration(initText, pkgResourcesInitPatterns)
  }
  return false
}

private val pkgResourcesInitPatterns = listOf(
  Pattern.compile("^__import__\\(['\"]pkg_resources['\"]\\).declare_namespace\\(__name__\\)\\s*\$"),
  Pattern.compile("^from pkg_resources import declare_namespace\\Rdeclare_namespace\\(__name__\\)\\s*\$"),
  Pattern.compile("^import pkg_resources\\Rpkg_resources.declare_namespace\\(__name__\\)\\s*\$"))


private val pkgutilsInitPatterns = listOf(
  Pattern.compile("^__path__[ ]?=[ ]?__import__\\(['\"]pkgutil['\"]\\).extend_path\\(__path__, __name__\\)\\s*\$"),
  Pattern.compile("^from pkgutil import extend_path\\R__path__[ ]?=[ ]?extend_path\\(__path__,[ ]?__name__\\)\\s*\$"),
  Pattern.compile("^import pkgutil\\R__path__[ ]?=[ ]?pkgutil\\.extend_path\\(__path__,[ ]?__name__\\)\\s*\$"))

private fun isNamespaceDeclaration(text: String, initTextPatterns: List<Pattern>): Boolean =
  initTextPatterns.asSequence().map { it.matcher(text) }.any { it.matches() }


private fun isUserFile(element: PsiElement, module: Module?) =
  module != null &&
  element is PsiFileSystemItem &&
  element.virtualFile.let { it != null && ModuleUtilCore.moduleContainsFile(module, it, false) }

private fun isInTypeShed(element: PsiElement) =
  PyiUtil.isPyiFileOfPackage(element) && (element as? PsiFileSystemItem)?.virtualFile.let { it != null && PyTypeShed.isInside(it) }

/**
 * See [https://www.python.org/dev/peps/pep-0561/#type-checker-module-resolution-order].
 * Order is important, see [filterTopPriorityResults].
 */
private enum class Priority {
  USER_STUB, // pyi file located in user's project
  USER_CODE, // py file located in user's project
  PROVIDED_STUB, // pyi file provided with installed lib and located inside it
  STUB_PACKAGE, // pyi file located in some stub package
  INLINE_PACKAGE, // py file located in some inline package
  TYPESHED, // pyi file located in typeshed
  OTHER, // other cases, e.g. py file located inside installed lib
  NAMESPACE_PACKAGE // namespace packag e has the lowest priority
}
