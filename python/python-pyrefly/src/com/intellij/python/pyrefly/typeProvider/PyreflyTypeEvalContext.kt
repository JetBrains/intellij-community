package com.intellij.python.pyrefly.typeProvider

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.util.getOffsetInDocument
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.util.getLsp4jPosition
import com.intellij.platform.lsp.util.getOffsetInDocument
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.python.lsp.core.type.LspTypeEvalContext
import com.intellij.python.lsp.core.type.PyStringTypeResolver
import com.intellij.python.pyrefly.PyreflyUsageCollector
import com.intellij.python.pyrefly.lsp.PyreflyLsp4jServer
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.PyCallable
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTypeParameter
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.impl.ParamHelper
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.PyAnyType
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.PyCallableParameterImpl
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyClassTypeImpl
import com.jetbrains.python.psi.types.PyCollectionTypeImpl
import com.jetbrains.python.psi.types.PyFunctionType
import com.jetbrains.python.psi.types.PyFunctionTypeImpl
import com.jetbrains.python.psi.types.PyLiteralType
import com.jetbrains.python.psi.types.PyModuleType
import com.jetbrains.python.psi.types.PyNeverType
import com.jetbrains.python.psi.types.PyOverloadType
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeVarType
import com.jetbrains.python.psi.types.PyTypeVarTypeImpl
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Unmodifiable
import org.jetbrains.annotations.VisibleForTesting
import kotlin.time.measureTimedValue

@Suppress("SSBasedInspection")
open class PyreflyTypeEvalContext internal constructor(val lspClient: LspClient, psiFile: PsiFile) : LspTypeEvalContext(psiFile) {

  private val snapshot: Int? by lazy {
    try {
      lspClient.sendRequestSync(LspClient.DEFAULT_REQUEST_TIMEOUT_MS) {
        (it as PyreflyLsp4jServer).getSnapshot()
      }
    }
    catch (e: ResponseErrorException) {
      e.responseError?.code
      null
    }
  }

  override fun provideType(element: PyTypedElement, isUserInitiated: Boolean): Ref<PyType?>? {
    if (!Registry.`is`("pyrefly.type.engine.tsp")) {
      // Old way: fetch types over LSP via the base-class flow (requestTypes / resolveStringType).
      return super.provideType(element, isUserInitiated)
    }
    if (element is PsiFile) return null
    val file = psiFile.virtualFile ?: return null
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
    val sourceUri = lspClient.getDocumentIdentifier(file).uri

    val textRange = element.textRange
    if (textRange.isEmpty) return null

    val offsetDetector = PyreflyOffsetDetectorVisitor()
    element.accept(offsetDetector)
    val position = getLsp4jPosition(document, offsetDetector.offset)
    val node = PyreflyLsp4jServer.TspNode(sourceUri, Range(position, position))

    val snapshot = this.snapshot ?: return null
    val tspType = try {
      lspClient.sendRequestSync(LspClient.DEFAULT_REQUEST_TIMEOUT_MS) {
        (it as PyreflyLsp4jServer).getComputedType(
          PyreflyLsp4jServer.GetComputedTypeParams(arg = node, snapshot = snapshot)
        )
      }
    }
    catch (e: ResponseErrorException) {
      e.responseError?.code
      null
    } ?: return null

    //logTypeDefinition(node, tspType)

    return buildPyType(element, tspType)
  }

  private fun buildPyType(pyElement: PyTypedElement, tspType: PyreflyLsp4jServer.TspType): Ref<PyType?>? {
    // Pyrefly marks `Literal[...]` types via the LITERAL bit (0x8) of TypeFlags and stores
    // the value in `literalValue`. Detect this before falling through to the generic
    // class-type path so we surface `Literal[5]` / `Literal["foo"]` instead of bare `int` /
    // `str` — otherwise `tuple[Literal[1], Literal[2], Literal[3]]` collapses to `tuple[int, int, int]`.
    if (tspType.kind == "3" && tspType.flags?.and(PyreflyLsp4jServer.LITERAL_FLAG) != 0) {
      buildPyLiteralType(pyElement, tspType)?.let { return it }
    }
    return when (tspType.kind) {
      "0" -> buildPyBuiltInType(pyElement, tspType)
      "2" -> buildPyFunctionType(pyElement, tspType)
      "3" -> buildPyClassType(pyElement, tspType)
      "4" -> buildPyUnionType(pyElement, tspType)
      "5" -> buildPyModuleType(pyElement, tspType)
      "6" -> buildPyTypeVarType(pyElement, tspType)
      "7" -> buildPyOverloadedType(pyElement, tspType)
      else -> null
    }
  }

  private data class ResolvedDefTarget(val file: PyFile, val element: PsiElement, val offset: Int)

  /**
   * Resolve the [PyFile] and the [PsiElement] (plus its offset) that a TSP declaration node points to.
   * Must be called inside a read action.
   */
  private fun resolveDefTarget(pyElement: PyTypedElement, defNode: PyreflyLsp4jServer.TspNode): ResolvedDefTarget? {
    val virtualFile = VirtualFileManager.getInstance().findFileByUrl(defNode.uri) ?: return null
    val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return null
    val offset = getOffsetInDocument(document, defNode.range.start) ?: return null
    val targetFile = pyElement.manager.findFile(virtualFile) as? PyFile ?: return null
    val targetElement = targetFile.findElementAt(offset) ?: return null
    return ResolvedDefTarget(targetFile, targetElement, offset)
  }

  /**
   * Build a `PyLiteralType` for a Class-kind TSP type that carries `LITERAL` flag.
   *
   * Pyrefly emits the literal payload as `literalValue` — Kotlin sees it as `Int`,
   * `Boolean`, or `String` after Gson-deserialization of the untagged TSP enum.
   * Bytes / enum / sentinel literals come through as JSON objects or `null` from our
   * `Any?` field and fall back to the regular class-type path.
   */
  private fun buildPyLiteralType(pyElement: PyTypedElement, tspType: PyreflyLsp4jServer.TspType): Ref<PyType?>? {
    val value = tspType.literalValue ?: return null
    return runReadActionBlocking {
      val literal = PyLiteralType.fromLiteralValue(pyElement, value)
      literal?.let {
        thisLogger().info("Pyrefly TSP: built PyLiteralType ${it.name}")
        Ref.create<PyType?>(it)
      }
    }
  }

  private fun buildPyBuiltInType(pyElement: PyTypedElement, tspType: PyreflyLsp4jServer.TspType): Ref<PyType?>? {
    val name = tspType.name ?: return null
    val type: PyType? = when (name.lowercase()) {
      "any" -> PyAnyType.any
      "never" -> PyNeverType.NEVER
      "noreturn" -> PyNeverType.NO_RETURN
      "ellipsis" -> runReadActionBlocking { PyBuiltinCache.getInstance(pyElement).ellipsisType }
      "unknown" -> PyAnyType.unknown
      "unbound" -> null
      "callable" -> runReadActionBlocking { PyTypingTypeProvider.createTypingCallableType(pyElement) }
      else -> runReadActionBlocking {
        PyClassTypeImpl.createTypeByQName(pyElement, "typing.$name", false)
        ?: PyClassTypeImpl.createTypeByQName(pyElement, PyTypingTypeProvider.SPECIAL_FORM, false)
      }
    }
    if (type == null) return null
    thisLogger().info("Pyrefly TSP: built builtin type for $name")
    return Ref.create(type)
  }

  private fun buildPyClassType(pyElement: PyTypedElement, tspType: PyreflyLsp4jServer.TspType): Ref<PyType?>? {
    val declaration = tspType.declaration ?: return null
    val defNode = declaration.node
    if (defNode == null || defNode.uri.isEmpty()) {
      // Pyrefly emits a synthesized stub class with `typeArgs` for built-in generic instances
      // such as `tuple[...]`. Recover the element types from `typeArgs`.
      if (!tspType.typeArgs.isNullOrEmpty()) {
        buildPyTupleType(pyElement, tspType.typeArgs)?.let { return it }
      }
      return buildBuiltinClassType(pyElement, declaration.name)
    }
    val resolved = runReadActionBlocking {
      val (_, targetElement, offset) = resolveDefTarget(pyElement, defNode) ?: return@runReadActionBlocking null
      val pyClass = PsiTreeUtil.getParentOfType(targetElement, PyClass::class.java) ?: return@runReadActionBlocking null
      val typeArgs = tspType.typeArgs
      val classType: PyType = if (!typeArgs.isNullOrEmpty()) {
        val elementTypes = typeArgs.map { buildPyType(pyElement, it)?.get() }
        if (pyClass.qualifiedName == PyNames.FQN.TUPLE) {
          PyTupleType.create(pyElement, elementTypes) ?: PyClassTypeImpl(pyClass, false)
        }
        else {
          PyCollectionTypeImpl(pyClass, false, elementTypes)
        }
      }
      else {
        PyClassTypeImpl(pyClass, false)
      }
      thisLogger().info("Pyrefly TSP: built PyClassType for ${pyClass.qualifiedName ?: pyClass.name} at ${defNode.uri}:$offset (typeArgs=${typeArgs?.size ?: 0})")
      Ref.create<PyType?>(classType)
    }
    if (resolved != null) return resolved
    // Pyrefly emits a sentinel `range=(0,0)` for builtin instances (e.g. `int` for the literal
    // default value `5`). The offset points to the start of `builtins.pyi`, so PSI gives us no
    // class — fall back to looking up the class by `declaration.name` in the builtin cache.
    return buildBuiltinClassType(pyElement, declaration.name)
  }

  private fun buildBuiltinClassType(pyElement: PyTypedElement, name: String?): Ref<PyType?>? {
    if (name.isNullOrEmpty()) return null
    return runReadActionBlocking {
      val type = PyBuiltinCache.getInstance(pyElement).getObjectType(name) ?: return@runReadActionBlocking null
      thisLogger().info("Pyrefly TSP: built PyClassType for builtin $name")
      Ref.create<PyType?>(type)
    }
  }

  private fun buildPyTupleType(pyElement: PyTypedElement, typeArgs: List<PyreflyLsp4jServer.TspType>): Ref<PyType?>? {
    if (typeArgs.isEmpty()) return null
    return runReadActionBlocking {
      val elementTypes = typeArgs.map { buildPyType(pyElement, it)?.get() }
      val tupleType = PyTupleType.create(pyElement, elementTypes) ?: return@runReadActionBlocking null
      thisLogger().info("Pyrefly TSP: built PyTupleType with ${elementTypes.size} elements")
      Ref.create<PyType?>(tupleType)
    }
  }

  private fun buildPyFunctionType(pyElement: PyTypedElement, tspType: PyreflyLsp4jServer.TspType): Ref<PyType?>? {
    val declaration = tspType.declaration ?: return null
    val defNode = declaration.node
    if (defNode == null || defNode.uri.isEmpty()) {
      // Pyrefly wraps class/type references in a Function envelope whose top-level
      // declaration is a Synthesized stub. The real symbol lives on `returnType`.
      val returnType = tspType.returnType
      if (returnType != null && returnType.hasSourceLocation()) {
        return buildPyType(pyElement, returnType)
      }
      return buildBuiltinFunctionType(pyElement, declaration.name, tspType)
    }
    val resolved = runReadActionBlocking {
      val (_, targetElement, offset) = resolveDefTarget(pyElement, defNode) ?: return@runReadActionBlocking null
      val callable = PsiTreeUtil.getParentOfType(targetElement, PyCallable::class.java) ?: return@runReadActionBlocking null
      thisLogger().info("Pyrefly TSP: built PyFunctionType for ${callable.name} at ${defNode.uri}:$offset")
      Ref.create<PyType?>(buildFunctionType(pyElement, callable, tspType))
    }
    if (resolved != null) return resolved
    // Sentinel `range=(0,0)` for builtin callables: PSI lookup at offset 0 misses the actual
    // `def`, so resolve by `declaration.name` via the builtin cache.
    return buildBuiltinFunctionType(pyElement, declaration.name, tspType)
  }

  private fun buildBuiltinFunctionType(pyElement: PyTypedElement, name: String?, tspType: PyreflyLsp4jServer.TspType): Ref<PyType?>? {
    if (name.isNullOrEmpty()) return null
    return runReadActionBlocking {
      val callable = PyBuiltinCache.getInstance(pyElement).getByName(name) as? PyCallable ?: return@runReadActionBlocking null
      thisLogger().info("Pyrefly TSP: built PyFunctionType for builtin $name")
      Ref.create<PyType?>(buildFunctionType(pyElement, callable, tspType))
    }
  }

  private fun buildFunctionType(
    pyElement: PyTypedElement,
    callable: PyCallable,
    tspType: PyreflyLsp4jServer.TspType,
  ): PyFunctionType {
    val psiParams = callable.parameterList.parameters
    val substituted = tspType.specializedTypes?.parameterTypes
    val parameters = psiParams.mapIndexed { i, p ->
      val resolved = substituted?.getOrNull(i)?.let { buildPyType(pyElement, it)?.get() }
      if (resolved != null) PyCallableParameterImpl.psi(p, resolved) else PyCallableParameterImpl.psi(p)
    }
    val returnTsp = tspType.specializedTypes?.returnType ?: tspType.returnType
    val returnType = returnTsp?.let { buildPyType(pyElement, it)?.get() }
    return PyreflyFunctionType(callable, parameters, returnType)
  }

  private class PyreflyFunctionType(
    callable: PyCallable,
    parameters: List<PyCallableParameter>,
    private val tspReturnType: PyType?,
  ) : PyFunctionTypeImpl(callable, parameters) {
    override fun getReturnType(context: TypeEvalContext): PyType? = tspReturnType

    override fun dropSelf(context: TypeEvalContext): PyFunctionType {
      val params = getParameters(context) ?: return this
      val newParams = ParamHelper.dropSelf(params)
      return if (newParams.size < params.size) PyreflyFunctionType(callable, newParams, tspReturnType) else this
    }
  }

  private fun buildPyUnionType(pyElement: PyTypedElement, tspType: PyreflyLsp4jServer.TspType): Ref<PyType?>? {
    val subTypes = tspType.subTypes ?: return null
    if (subTypes.isEmpty()) return null
    val members = subTypes.map { buildPyType(pyElement, it)?.get() }
    if (members.all { it == null }) return null
    thisLogger().info("Pyrefly TSP: built PyUnionType with ${members.size} members")
    return Ref.create(PyUnionType.union(members))
  }

  private fun buildPyModuleType(pyElement: PyTypedElement, tspType: PyreflyLsp4jServer.TspType): Ref<PyType?>? {
    val uri = tspType.uri
    if (uri.isNullOrEmpty()) return null
    val virtualFile = VirtualFileManager.getInstance().findFileByUrl(uri) ?: return null
    return runReadActionBlocking {
      val moduleFile = pyElement.manager.findFile(virtualFile) as? PyFile ?: return@runReadActionBlocking null
      thisLogger().info("Pyrefly TSP: built PyModuleType for ${tspType.moduleName ?: moduleFile.name}")
      Ref.create<PyType?>(PyModuleType(moduleFile))
    }
  }

  private fun buildPyTypeVarType(pyElement: PyTypedElement, tspType: PyreflyLsp4jServer.TspType): Ref<PyType?>? {
    val declaration = tspType.declaration ?: return null
    val name = declaration.name
    if (name.isNullOrEmpty()) return null
    val defNode = declaration.node
    if (defNode == null || defNode.uri.isEmpty()) {
      thisLogger().info("Pyrefly TSP: built minimal PyTypeVarType for $name (no declaration node)")
      return Ref.create<PyType?>(PyTypeVarTypeImpl(name, null))
    }
    val resolved = runReadActionBlocking {
      val (targetFile, targetElement, offset) = resolveDefTarget(pyElement, defNode) ?: return@runReadActionBlocking null
      val context = TypeEvalContext.codeAnalysis(pyElement.project, targetFile)

      val typeParameter = PsiTreeUtil.getParentOfType(targetElement, PyTypeParameter::class.java)
      if (typeParameter != null) {
        val pep695Type = PyTypingTypeProvider.getTypeParameterTypeFromTypeParameter(typeParameter, context)
        if (pep695Type is PyTypeVarType) {
          thisLogger().info("Pyrefly TSP: built PyTypeVarType for $name from PyTypeParameter at ${defNode.uri}:$offset")
          return@runReadActionBlocking Ref.create<PyType?>(pep695Type)
        }
      }

      val targetExpression = PsiTreeUtil.getParentOfType(targetElement, PyTargetExpression::class.java)
      if (targetExpression != null) {
        val assignedType = context.getType(targetExpression)
        if (assignedType is PyTypeVarType) {
          thisLogger().info("Pyrefly TSP: built PyTypeVarType for $name from PyTargetExpression at ${defNode.uri}:$offset")
          return@runReadActionBlocking Ref.create<PyType?>(assignedType)
        }
      }

      null
    }
    if (resolved != null) return resolved
    thisLogger().info("Pyrefly TSP: fell back to minimal PyTypeVarType for $name")
    return Ref.create<PyType?>(PyTypeVarTypeImpl(name, null))
  }

  private fun buildPyOverloadedType(pyElement: PyTypedElement, tspType: PyreflyLsp4jServer.TspType): Ref<PyType?>? {
    val overloads = tspType.overloads
    val implementationTsp = tspType.implementation
    if (overloads.isNullOrEmpty() && implementationTsp == null) return null
    val items = overloads?.map { buildPyType(pyElement, it)?.get() as? PyCallableType } ?: emptyList()
    val impl = implementationTsp?.let { buildPyType(pyElement, it) }
    if (items.isEmpty() && impl == null) return null
    thisLogger().info("Pyrefly TSP: built PyOverloadType with ${items.size} overloads (impl=${impl?.get() != null})")
    return Ref.create<PyType?>(PyOverloadType(items, impl))
  }

  override fun requestTypes(pyTypedElements: @Unmodifiable Collection<PyTypedElement>): List<String?>? {
    val file = psiFile.virtualFile ?: return null
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
    val textDocument = lspClient.getDocumentIdentifier(file)

    val positions = pyTypedElements.mapNotNull { psiElement ->
      if (psiElement is PsiFile) return@mapNotNull null

      val textRange = psiElement.textRange
      if (textRange.isEmpty) return@mapNotNull null

      val offsetDetector = PyreflyOffsetDetectorVisitor()
      psiElement.accept(offsetDetector)
      val offset = offsetDetector.offset
      getLsp4jPosition(document, offset)
    }

    val response = try {
      lspClient.sendRequestSync {
        (it as PyreflyLsp4jServer).provideType(textDocument, positions)
      }
    }
    catch (e: ResponseErrorException) {
      e.responseError?.code
      return null
    }
    return response?.contents?.map { it.value }
  }

  override fun resolveStringType(element: PyTypedElement, stringType: String): Ref<PyType?>? {
    return resolveStringTypeImpl(element, stringType)
  }

  private fun PyreflyLsp4jServer.TspType?.hasSourceLocation(): Boolean {
    val type = this ?: return false
    val decl = type.declaration
    if (decl != null) {
      val nodeUri = decl.node?.uri
      if (!nodeUri.isNullOrEmpty() || !decl.uri.isNullOrEmpty()) return true
    }
    // Class/type references wrapped in a Function envelope keep their real declaration on returnType.
    return type.returnType?.hasSourceLocation() == true
  }

  companion object {
    @ApiStatus.Internal
    @VisibleForTesting
    fun resolveStringTypeImpl(
      element: PyTypedElement,
      stringType: String,
    ): Ref<PyType?>? {
      val (result, duration) = measureTimedValue {
        PyStringTypeResolver.resolvePyType(element, stringType)
      }
      PyreflyUsageCollector.logStringTypeResolutionTime(duration)
      return result
    }
  }
}
