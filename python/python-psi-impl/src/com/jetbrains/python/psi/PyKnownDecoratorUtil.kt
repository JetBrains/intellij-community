// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.FunctionParameter
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.resolve.fromFoothold
import com.jetbrains.python.psi.resolve.resolveQualifiedName
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.pyi.PyiFile
import one.util.streamex.StreamEx
import org.jetbrains.annotations.ApiStatus

/**
 * Contains list of well-behaved decorators from Pythons standard library, that don't change
 * signature of underlying function/class or use it implicitly somewhere (e.g. register as a callback).
 * 
 * @author Mikhail Golubev
 */
object PyKnownDecoratorUtil {
  /**
   * Map decorators of element to [PyKnownDecorator].
   *
   * @param element decoratable element to check
   * @param context type evaluation context. If it doesn't allow switch to AST, the imports of the file give the qualified name of a decorator.
   * @return list of known decorators in declaration order with duplicates (with any)
   */
  @JvmStatic
  fun getKnownDecorators(element: PyDecoratable, context: TypeEvalContext): List<PyKnownDecorator> {
    val decoratorList = element.decoratorList
    if (decoratorList == null) {
      return emptyList()
    }

    return decoratorList.decorators
      .flatMap { asKnownDecorators(it, context) }
  }

  @JvmStatic
  fun asKnownDecorators(decorator: PyDecorator, context: TypeEvalContext): List<PyKnownDecorator> {
    val qualifiedName = decorator.qualifiedName
    if (qualifiedName == null) {
      return emptyList()
    }
    // Avoid resolving property accessor decorators to prevent an infinite recursion
    val lastComponent = qualifiedName.lastComponent
    if (PyNames.GETTER == lastComponent || PyNames.SETTER == lastComponent || PyNames.DELETER == lastComponent) {
      return emptyList()
    }
    val containingFile = decorator.containingFile
    if (!context.maySwitchToAST(decorator)) {
      // Without the AST, the imports of the file give the qualified name of the decorator. This reads stubs only.
      if (containingFile !is PyFile) return emptyList()
      return asKnownDecoratorsInFile(qualifiedName, containingFile)
    }
    val resolved = if (containingFile is PyiFile) {
      // In .pyi files it's safe to resolve decorators such as "@overload" flow-insensitively.
      PyResolveUtil.resolveQualifiedNameInScope(qualifiedName, containingFile, context)
    }
    else {
      PyUtil.multiResolveTopPriority(decorator.callee!!, PyResolveContext.defaultContext(context))
    }
    return resolved
      .filterIsInstance<PyQualifiedNameOwner>()
      .mapNotNull { it.qualifiedName }
      .map(PyNames.FQN::unqualifyBuiltinName)
      .map { QualifiedName.fromDottedString(it!!) }
      .mapNotNull { findByQualifiedName(it) }
  }

  /**
   * Returns the known decorators that [qualifiedName], as it is written in [file], can point to.
   *
   * The method reads the import statements of the file and resolves no name in a scope, so it reads stubs only.
   * It sees a top-level import only, which includes an import under `if TYPE_CHECKING:`.
   *
   * An import gives the module of a name, but the file cannot tell where that module takes the name from.
   * A decorator of the same top-level package matches first.
   * If the package holds no such decorator, the method reads the imports of that module in the same way, and there it
   * also follows a relative import into the package of that module. So a module that re-exports a decorator, as
   * `propcache._helpers_py` re-exports `functools.cached_property`, gives it, and a module that defines a name of its
   * own, as `cached_property = CachedProperty`, gives none.
   * Only when a module cannot be found does the short name match.
   * A relative import of the file itself comes from the package of the file, which holds no known decorator, so the
   * name is not one.
   * A name that the file itself defines belongs to the module of the file, as `classmethod` does in `builtins.pyi`.
   * For any other name the short name is all the file gives. This covers a builtin, a name from a star import,
   * an import inside a function, and a member of a local object as in `@my_property.expression`.
   */
  private fun asKnownDecoratorsInFile(qualifiedName: QualifiedName, file: PyFile): List<PyKnownDecorator> {
    return PyUtil.getParameterizedCachedValue(file, qualifiedName) { name ->
      computeKnownDecoratorsInFile(name, file, mutableSetOf(), imported = false) ?: emptyList()
    }
  }

  /**
   * Returns the known decorators that [qualifiedName] points to in [file].
   * For a module that a name is [imported] from, returns `null` when a module on the way cannot be found.
   */
  private fun computeKnownDecoratorsInFile(
    qualifiedName: QualifiedName,
    file: PyFile,
    visited: MutableSet<Pair<PyFile, QualifiedName>>,
    imported: Boolean,
  ): List<PyKnownDecorator>? {
    // Two modules can import a name from each other, and two imports can reach one module. The first visit tells.
    if (!visited.add(file to qualifiedName)) return null
    val firstName = qualifiedName.firstComponent ?: return emptyList()
    val tail = qualifiedName.removeHead(1)
    // A star import keeps the name and gives no imported name, so the short name still finds the decorator further down.
    val (importedNames, boundByRelativeImport) = collectImportedNames(qualifiedName, file)
    if (boundByRelativeImport && !imported) return emptyList()

    val known = importedNames.flatMap { importedName ->
      val byQualifiedName = findByQualifiedName(importedName)
      if (byQualifiedName != null) return@flatMap listOf(byQualifiedName)
      val byShortName = findByShortName(importedName.lastComponent!!)
      if (byShortName.isEmpty()) return@flatMap emptyList()
      byShortName.filter { it.qualifiedName.firstComponent == importedName.firstComponent }.ifEmpty {
        knownDecoratorsInModule(importedName, file, visited) ?: byShortName
      }
    }
    if (boundByRelativeImport) {
      // A module can bind the name by an absolute import in one branch and by a relative one in another
      val relative = knownDecoratorsOfRelativeImport(qualifiedName, file, visited)
      return if (relative == null && importedNames.isEmpty()) null else (known + relative.orEmpty()).distinct()
    }
    if (importedNames.isNotEmpty()) return known
    val defined = definedName(file, firstName)
    if (defined != null) {
      val definedQName = (defined as? PyQualifiedNameOwner)?.qualifiedName ?: return emptyList()
      val name = PyNames.FQN.unqualifyBuiltinName(definedQName)!!
      return listOfNotNull(findByQualifiedName(QualifiedName.fromDottedString(name).append(tail)))
    }
    return findByShortName(qualifiedName.lastComponent!!)
  }

  /**
   * Returns the absolute names that the top-level imports of [file] give to [qualifiedName], as it is written in [file].
   * Returns `null` when a relative import binds the first component of [qualifiedName].
   * The method reads the import statements of the file and resolves nothing, so it stays inside the stubs of the file.
   */
  @ApiStatus.Internal
  @JvmStatic
  fun importedQualifiedNames(qualifiedName: QualifiedName, file: PyFile): List<QualifiedName>? {
    val (importedNames, boundByRelativeImport) = collectImportedNames(qualifiedName, file)
    return if (boundByRelativeImport) null else importedNames
  }

  /**
   * Returns the absolute names that the top-level imports of [file] give to [qualifiedName],
   * and whether a relative import binds the first component of [qualifiedName] too.
   */
  private fun collectImportedNames(qualifiedName: QualifiedName, file: PyFile): Pair<List<QualifiedName>, Boolean> {
    val firstName = qualifiedName.firstComponent ?: return emptyList<QualifiedName>() to false
    val tail = qualifiedName.removeHead(1)
    val importedNames = mutableListOf<QualifiedName>()
    var boundByRelativeImport = false

    for (importElement in file.importTargets) {
      val importedQName = importElement.importedQName ?: continue
      val asName = importElement.asName
      if (asName != null) {
        if (asName == firstName) importedNames.add(importedQName.append(tail))
      }
      else if (importedQName.firstComponent == firstName) {
        // "import a.b" binds "a", so the name is already absolute.
        importedNames.add(qualifiedName)
      }
    }
    for (fromImport in file.fromImports) {
      if (fromImport.isStarImport) continue
      if (fromImport.importElements.none { it.visibleName == firstName }) continue
      if (fromImport.relativeLevel != 0) {
        boundByRelativeImport = true
        continue
      }
      val source = fromImport.importSourceQName ?: continue
      for (importElement in fromImport.importElements) {
        if (importElement.visibleName == firstName) {
          importElement.importedQName?.let { importedNames.add(source.append(it).append(tail)) }
        }
      }
    }
    return importedNames to boundByRelativeImport
  }

  /**
   * Returns the known decorators that the module of [importedName] gives to its last component,
   * or `null` when the module cannot be found from [file].
   */
  private fun knownDecoratorsInModule(
    importedName: QualifiedName,
    file: PyFile,
    visited: MutableSet<Pair<PyFile, QualifiedName>>,
  ): List<PyKnownDecorator>? {
    if (importedName.componentCount < 2) return null
    val modules = resolveQualifiedName(importedName.removeLastComponent(), fromFoothold(file))
      .mapNotNull { PyUtil.turnDirIntoInit(it) as? PyFile }
    val name = QualifiedName.fromComponents(importedName.lastComponent!!)
    return knownDecoratorsInModules(modules.map { it to name }, visited)
  }

  /**
   * Returns the known decorators that the relative imports of [file] give to [qualifiedName],
   * or `null` when the module of such an import cannot be found.
   */
  private fun knownDecoratorsOfRelativeImport(
    qualifiedName: QualifiedName,
    file: PyFile,
    visited: MutableSet<Pair<PyFile, QualifiedName>>,
  ): List<PyKnownDecorator>? {
    val firstName = qualifiedName.firstComponent ?: return emptyList()
    val tail = qualifiedName.removeHead(1)
    val names = file.fromImports
      .filter { !it.isStarImport && it.relativeLevel != 0 }
      .flatMap { fromImport ->
        val elements = fromImport.importElements.filter { it.visibleName == firstName }
        if (elements.isEmpty()) return@flatMap emptyList()
        val modules = fromImport.resolveImportSourceCandidates().mapNotNull { PyUtil.turnDirIntoInit(it) as? PyFile }
        modules.flatMap { module -> elements.mapNotNull { element -> element.importedQName?.let { module to it.append(tail) } } }
      }
    return knownDecoratorsInModules(names, visited)
  }

  /** Returns the known decorators of the names in their modules, or `null` when no module tells. */
  private fun knownDecoratorsInModules(
    names: List<Pair<PyFile, QualifiedName>>,
    visited: MutableSet<Pair<PyFile, QualifiedName>>,
  ): List<PyKnownDecorator>? {
    val results = names.mapNotNull { (module, name) -> computeKnownDecoratorsInFile(name, module, visited, imported = true) }
    return if (results.isEmpty()) null else results.flatten().distinct()
  }

  private fun definedName(file: PyFile, name: String): PsiElement? {
    return file.findTopLevelClass(name) ?: file.findTopLevelFunction(name) ?: file.findTopLevelAttribute(name)
  }

  @ApiStatus.Internal
  @JvmStatic
  fun asKnownDecorators(qualifiedName: QualifiedName): List<PyKnownDecorator> {
    // The method might have been called during building of PSI stub indexes. Thus, we can't leave this file's boundaries.
    // TODO Use proper local resolve to imported names here
    val lastComponent = qualifiedName.lastComponent
    if (lastComponent == null) {
      return emptyList()
    }
    return findByShortName(lastComponent)
  }

  /**
   * Check that given element has any non-standard (read "unreliable") decorators.
   *
   * @param element decoratable element to check
   * @param context type evaluation context. If it doesn't allow switch to AST, the imports of the file give the qualified name of a decorator.
   * @see PyKnownDecorator
   */
  @JvmStatic
  fun hasUnknownDecorator(element: PyDecoratable, context: TypeEvalContext): Boolean {
    return !allDecoratorsAreKnown(element, getKnownDecorators(element, context))
  }

  /**
   * Checks that given function has any decorators from `abc` module.
   *
   * @param element Python function to check
   * @param context type evaluation context. If it doesn't allow switch to AST, the imports of the file give the qualified name of a decorator.
   * @see PyKnownDecorator
   */
  @JvmStatic
  fun hasAbstractDecorator(element: PyDecoratable, context: TypeEvalContext): Boolean {
    return getKnownDecorators(element, context).any { it.isAbstract }
  }

  @JvmStatic
  fun hasGeneratorBasedCoroutineDecorator(function: PyFunction, context: TypeEvalContext): Boolean {
    return getKnownDecorators(function,
                              context).any { it.isGeneratorBasedCoroutine }
  }

  @JvmStatic
  fun isResolvedToGeneratorBasedCoroutine(
    receiver: PyCallExpression,
    resolveContext: PyResolveContext,
    typeEvalContext: TypeEvalContext,
  ): Boolean {
    return StreamEx
      .of((receiver).multiResolveCalleeFunction(resolveContext))
      .select(PyFunction::class.java)
      .anyMatch { function -> hasGeneratorBasedCoroutineDecorator(function, typeEvalContext) }
  }

  @JvmStatic
  fun hasRedeclarationDecorator(function: PyFunction, context: TypeEvalContext): Boolean {
    return getKnownDecorators(function, context).contains(PyKnownDecorator.TYPING_OVERLOAD)
  }

  @JvmStatic
  fun findOverrideDecorator(decoratable: PyDecoratable, context: TypeEvalContext): PyDecorator? {
    return decoratable.decoratorList?.decorators?.firstOrNull { decorator ->
      asKnownDecorators(decorator, context).any {
        it == PyKnownDecorator.TYPING_OVERRIDE || it == PyKnownDecorator.TYPING_EXTENSIONS_OVERRIDE
      }
    }
  }

  @JvmStatic
  fun hasOverrideDecorator(decoratable: PyDecoratable, context: TypeEvalContext): Boolean {
    return findOverrideDecorator(decoratable, context) != null
  }

  @JvmStatic
  fun hasUnknownOrChangingSignatureDecorator(decoratable: PyDecoratable, context: TypeEvalContext): Boolean {
    val decorators = getKnownDecorators(decoratable, context)
    return !allDecoratorsAreKnown(decoratable, decorators) || decorators.contains(PyKnownDecorator.UNITTEST_MOCK_PATCH)
  }

  @JvmStatic
  fun hasUnknownOrUpdatingAttributesDecorator(decoratable: PyDecoratable, context: TypeEvalContext): Boolean {
    val decorators = getKnownDecorators(decoratable, context)

    if (!allDecoratorsAreKnown(decoratable, decorators)) {
      return true
    }

    return decorators.any {
      it === PyKnownDecorator.FUNCTOOLS_LRU_CACHE ||  // cache_clear, cache_info
      it === PyKnownDecorator.FUNCTOOLS_SINGLEDISPATCH
    }
  }

  private fun allDecoratorsAreKnown(element: PyDecoratable, decorators: List<PyKnownDecorator>): Boolean {
    val decoratorList = element.decoratorList
    return if (decoratorList == null)
      decorators.isEmpty()
    else
      decoratorList.decorators.size == decorators
        .groupBy { it.shortName }.size
  }

  @Suppress("DEPRECATION")
  private fun findByShortName(shortName: String): List<PyKnownDecorator> {
    return PyKnownDecoratorProvider.EP_NAME.extensionList.stream()
      .flatMap { knownDecoratorProvider ->
        val decorators = knownDecoratorProvider.knownDecorators
        if (!decorators.isEmpty()) {
          return@flatMap decorators.stream()
        }
        // Fallback to the old implementation that will be removed in the future release
        val knownDecorator = knownDecoratorProvider.toKnownDecorator(shortName)
        if (!knownDecorator.isNullOrEmpty() && (knownDecorator != shortName)) {
          return@flatMap StreamEx.of(findByShortName(knownDecorator))
        }
        StreamEx.empty()
      }
      .filter { knownDecorator -> knownDecorator.shortName == shortName }
      .toList()
  }

  private fun findByQualifiedName(qualifiedName: QualifiedName): PyKnownDecorator? {
    return PyKnownDecoratorProvider.EP_NAME.extensionList.stream()
      .flatMap { knownDecoratorProvider: PyKnownDecoratorProvider? ->
        knownDecoratorProvider!!.knownDecorators.stream()
      }
      .filter { knownDecorator: PyKnownDecorator? -> knownDecorator!!.qualifiedName == qualifiedName }
      .findFirst()
      .orElse(null)
  }

  enum class FunctoolsWrapsParameters(private val myPosition: Int, private val myName: String) : FunctionParameter {
    WRAPPED(0, "wrapped");

    override fun getPosition(): Int {
      return myPosition
    }

    override fun getName(): String {
      return myName
    }
  }
}
