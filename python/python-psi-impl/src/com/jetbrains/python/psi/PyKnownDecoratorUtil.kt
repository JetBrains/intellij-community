// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.FunctionParameter
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
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
   * Returns the known decorators of [element], one entry per decorator in declaration order.
   *
   * @param context type evaluation context. If it doesn't allow switch to AST, the imports of the file give the qualified name of a decorator.
   */
  @JvmStatic
  fun knownDecoratorsOf(element: PyDecoratable, context: TypeEvalContext): PyElementKnownDecorators {
    val decoratorList = element.decoratorList ?: return PyElementKnownDecorators.NONE
    val decorators = decoratorList.decorators.map { knownDecoratorsOf(it, context) }
    return PyElementKnownDecorators.of(decorators)
  }

  @Deprecated(
    "Use knownDecoratorsOf(element, context). The returned list flattens two axes - which decorator, and which " +
    "known decorator each can resolve to - and holds candidates, not facts: aggregate it with any, never with all.",
    ReplaceWith("knownDecoratorsOf(element, context)")
  )
  @JvmStatic
  fun getKnownDecorators(element: PyDecoratable, context: TypeEvalContext): List<PyKnownDecorator> {
    return knownDecoratorsOf(element, context).asList()
  }

  /**
   * Returns the known decorators that [decorator] can resolve to.
   *
   * @param context type evaluation context. If it doesn't allow switch to AST, the imports of the file give the qualified name of a decorator.
   */
  @JvmStatic
  fun knownDecoratorsOf(decorator: PyDecorator, context: TypeEvalContext): PyKnownDecorators {
    val qualifiedName = decorator.qualifiedName ?: return PyKnownDecorators.NONE
    // Avoid resolving property accessor decorators to prevent an infinite recursion
    val lastComponent = qualifiedName.lastComponent
    if (PyNames.GETTER == lastComponent || PyNames.SETTER == lastComponent || PyNames.DELETER == lastComponent) {
      return PyKnownDecorators.NONE
    }
    val containingFile = decorator.containingFile
    if (!context.maySwitchToAST(decorator)) {
      // Without the AST, the imports of the file give the qualified name of the decorator. This reads stubs only.
      if (containingFile !is PyFile) return PyKnownDecorators.NONE
      return PyKnownDecorators.of(asKnownDecoratorsInFile(qualifiedName, containingFile))
    }
    val resolved = if (containingFile is PyiFile) {
      // In .pyi files it's safe to resolve decorators such as "@overload" flow-insensitively.
      PyResolveUtil.resolveQualifiedNameInScope(qualifiedName, containingFile, context)
    }
    else {
      PyUtil.multiResolveTopPriority(decorator.callee!!, PyResolveContext.defaultContext(context))
    }
    val candidates = resolved
      .filterIsInstance<PyQualifiedNameOwner>()
      .mapNotNull { it.qualifiedName }
      .map(PyNames.FQN::unqualifyBuiltinName)
      .map { QualifiedName.fromDottedString(it!!) }
      .mapNotNull { findByQualifiedName(it) }
    return PyKnownDecorators.of(candidates)
  }

  @Deprecated(
    "Use knownDecoratorsOf(decorator, context). The returned list holds resolve candidates, not facts: " +
    "aggregate it with any, never with all. The returned list is immutable.",
    ReplaceWith("knownDecoratorsOf(decorator, context)")
  )
  @JvmStatic
  fun asKnownDecorators(decorator: PyDecorator, context: TypeEvalContext): List<PyKnownDecorator> {
    return knownDecoratorsOf(decorator, context).asList()
  }

  /**
   * Returns the known decorators that [qualifiedName], as it is written in [file], can point to.
   *
   * The method reads the import statements of the file and resolves nothing, so it stays inside the stubs of the file.
   * It sees a top-level import only, which includes an import under `if TYPE_CHECKING:`.
   *
   * An import gives the module of a name, but the file cannot tell where that module takes the name from.
   * A decorator of the same top-level package matches first.
   * If the package holds no such decorator, the module can re-export one, as `propcache._helpers_py` re-exports
   * `functools.cached_property`, so the short name matches.
   * A relative import comes from the package of the file, which holds no known decorator, so the name is not one.
   * A name that the file itself defines belongs to the module of the file, as `classmethod` does in `builtins.pyi`.
   * For any other name the short name is all the file gives. This covers a builtin, a name from a star import,
   * an import inside a function, and a member of a local object as in `@my_property.expression`.
   */
  private fun asKnownDecoratorsInFile(qualifiedName: QualifiedName, file: PyFile): List<PyKnownDecorator> {
    return computeKnownDecoratorsInFile(qualifiedName, file)
  }

  private fun computeKnownDecoratorsInFile(qualifiedName: QualifiedName, file: PyFile): List<PyKnownDecorator> {
    val firstName = qualifiedName.firstComponent ?: return emptyList()
    val tail = qualifiedName.removeHead(1)
    val importedNames = mutableListOf<QualifiedName>()

    for (importElement in file.importTargets) {
      val importedQName = importElement.importedQName ?: continue
      val asName = importElement.asName
      if (asName != null) {
        if (asName == firstName) importedNames.add(importedQName.append(tail))
      }
      else if (importedQName.firstComponent == firstName) {
        // "import a.b" binds "a", so the name of the decorator is already absolute.
        importedNames.add(qualifiedName)
      }
    }
    for (fromImport in file.fromImports) {
      // A star import keeps the name, so the short name still finds the decorator further down.
      if (fromImport.isStarImport) continue
      if (fromImport.importElements.none { it.visibleName == firstName }) continue
      if (fromImport.relativeLevel != 0) return emptyList()
      val source = fromImport.importSourceQName ?: continue
      for (importElement in fromImport.importElements) {
        if (importElement.visibleName == firstName) {
          importElement.importedQName?.let { importedNames.add(source.append(it).append(tail)) }
        }
      }
    }

    if (importedNames.isNotEmpty()) {
      return importedNames.flatMap { importedName ->
        val byQualifiedName = findByQualifiedName(importedName)
        if (byQualifiedName != null) return@flatMap listOf(byQualifiedName)
        val byShortName = findByShortName(importedName.lastComponent!!)
        byShortName.filter { it.qualifiedName.firstComponent == importedName.firstComponent }.ifEmpty { byShortName }
      }
    }
    val defined = definedName(file, firstName)
    if (defined != null) {
      val definedQName = (defined as? PyQualifiedNameOwner)?.qualifiedName ?: return emptyList()
      val name = PyNames.FQN.unqualifyBuiltinName(definedQName)!!
      return listOfNotNull(findByQualifiedName(QualifiedName.fromDottedString(name).append(tail)))
    }
    return findByShortName(qualifiedName.lastComponent!!)
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
    return !knownDecoratorsOf(element, context).allKnown()
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
    return knownDecoratorsOf(element, context).hasTrait(PyKnownDecorator::isAbstract)
  }

  @JvmStatic
  fun hasGeneratorBasedCoroutineDecorator(function: PyFunction, context: TypeEvalContext): Boolean {
    return knownDecoratorsOf(function, context).hasTrait(PyKnownDecorator::isGeneratorBasedCoroutine)
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
    return knownDecoratorsOf(function, context).contains(PyKnownDecorator.TYPING_OVERLOAD)
  }

  @JvmStatic
  fun findOverrideDecorator(decoratable: PyDecoratable, context: TypeEvalContext): PyDecorator? {
    return decoratable.decoratorList?.decorators?.firstOrNull { decorator ->
      knownDecoratorsOf(decorator, context)
        .containsAny(PyKnownDecorator.TYPING_OVERRIDE, PyKnownDecorator.TYPING_EXTENSIONS_OVERRIDE)
    }
  }

  @JvmStatic
  fun hasOverrideDecorator(decoratable: PyDecoratable, context: TypeEvalContext): Boolean {
    return findOverrideDecorator(decoratable, context) != null
  }

  @JvmStatic
  fun hasUnknownOrChangingSignatureDecorator(decoratable: PyDecoratable, context: TypeEvalContext): Boolean {
    val decorators = knownDecoratorsOf(decoratable, context)
    return !decorators.allKnown() || decorators.contains(PyKnownDecorator.UNITTEST_MOCK_PATCH)
  }

  @JvmStatic
  fun hasUnknownOrUpdatingAttributesDecorator(decoratable: PyDecoratable, context: TypeEvalContext): Boolean {
    val decorators = knownDecoratorsOf(decoratable, context)
    if (!decorators.allKnown()) return true
    // lru_cache adds cache_clear and cache_info, singledispatch adds register
    return decorators.containsAny(PyKnownDecorator.FUNCTOOLS_LRU_CACHE, PyKnownDecorator.FUNCTOOLS_SINGLEDISPATCH)
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
