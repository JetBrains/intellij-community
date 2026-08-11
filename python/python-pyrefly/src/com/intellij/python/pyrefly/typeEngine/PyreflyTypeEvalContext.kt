package com.intellij.python.pyrefly.typeEngine

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.lsp.api.LspClient
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
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.PyAnyType
import com.jetbrains.python.psi.types.PyCallableParameterImpl
import com.jetbrains.python.psi.types.PyCallableParameterListTypeImpl
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyCallableTypeImpl
import com.jetbrains.python.psi.types.PyClassTypeImpl
import com.jetbrains.python.psi.types.PyCollectionTypeImpl
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
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Unmodifiable
import org.jetbrains.annotations.VisibleForTesting
import kotlin.time.measureTimedValue

open class PyreflyTypeEvalContext internal constructor(val lspClient: LspClient, psiFile: PsiFile) : LspTypeEvalContext(psiFile) {

  private val snapshot: Int? by lazy {
    lspClient.sendRequestSync { (it as PyreflyLsp4jServer).getSnapshot() }
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

    val snapshot = snapshot ?: return null
    val tspType = lspClient.sendRequestSync {
      (it as PyreflyLsp4jServer).getComputedType(PyreflyLsp4jServer.GetComputedTypeParams(arg = node, snapshot = snapshot))
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

  private fun findElement(tspNode: PyreflyLsp4jServer.TspNode): PsiElement? {
    val virtualFile = VirtualFileManager.getInstance().findFileByUrl(tspNode.uri) ?: return null
    val file = psiFile.manager.findFile(virtualFile) ?: return null
    val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return null
    val startOffset = getOffsetInDocument(document, tspNode.range.start) ?: return null
    val endOffset = getOffsetInDocument(document, tspNode.range.end) ?: return null
    return PsiTreeUtil.findElementOfClassAtRange(file, startOffset, endOffset, PsiElement::class.java)
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
    val literal = PyLiteralType.fromLiteralValue(pyElement, value) ?: return null
    thisLogger().info("Pyrefly TSP: built PyLiteralType ${literal.name}")
    return Ref.create(literal)
  }

  private fun buildPyBuiltInType(pyElement: PyTypedElement, tspType: PyreflyLsp4jServer.TspType): Ref<PyType?>? {
    val name = tspType.name ?: return null
    val type: PyType? = when (name.lowercase()) {
      "any" -> PyAnyType.any
      "never" -> PyNeverType.NEVER
      "noreturn" -> PyNeverType.NO_RETURN
      "ellipsis" -> PyBuiltinCache.getInstance(pyElement).ellipsisType
      "unknown" -> PyAnyType.unknown
      "unbound" -> null
      "callable" -> PyTypingTypeProvider.createTypingCallableType(pyElement)
      else -> PyClassTypeImpl.createTypeByQName(pyElement, "typing.$name", false)
              ?: PyClassTypeImpl.createTypeByQName(pyElement, PyTypingTypeProvider.SPECIAL_FORM, false)
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
    val target = findElement(defNode)
    val pyClass = target?.let { PsiTreeUtil.getParentOfType(it, PyClass::class.java) }
    if (pyClass == null) {
      // Pyrefly emits a sentinel `range=(0,0)` for builtin instances (e.g. `int` for the literal
      // default value `5`). The offset points to the start of `builtins.pyi`, so PSI gives us no
      // class — fall back to looking up the class by `declaration.name` in the builtin cache.
      return buildBuiltinClassType(pyElement, declaration.name)
    }
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
    thisLogger().info("Pyrefly TSP: built PyClassType for ${pyClass.qualifiedName ?: pyClass.name} at ${defNode.uri}:${defNode.range.start} (typeArgs=${typeArgs?.size ?: 0})")
    return Ref.create(classType)
  }

  private fun buildBuiltinClassType(pyElement: PyTypedElement, name: String?): Ref<PyType?>? {
    if (name.isNullOrEmpty()) return null
    val type = PyBuiltinCache.getInstance(pyElement).getObjectType(name) ?: return null
    thisLogger().info("Pyrefly TSP: built PyClassType for builtin $name")
    return Ref.create(type)
  }

  private fun buildPyTupleType(pyElement: PyTypedElement, typeArgs: List<PyreflyLsp4jServer.TspType>): Ref<PyType?>? {
    if (typeArgs.isEmpty()) return null
    val elementTypes = typeArgs.map { buildPyType(pyElement, it)?.get() }
    val tupleType = PyTupleType.create(pyElement, elementTypes) ?: return null
    thisLogger().info("Pyrefly TSP: built PyTupleType with ${elementTypes.size} elements")
    return Ref.create(tupleType)
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
    val target = findElement(defNode)
    val callable = target?.let { PsiTreeUtil.getParentOfType(it, PyCallable::class.java) }
    if (callable == null) {
      // Sentinel `range=(0,0)` for builtin callables: PSI lookup at offset 0 misses the actual
      // `def`, so resolve by `declaration.name` via the builtin cache.
      return buildBuiltinFunctionType(pyElement, declaration.name, tspType)
    }
    thisLogger().info("Pyrefly TSP: built PyFunctionType for ${callable.name} at ${defNode.uri}:${defNode.range.start}")
    return Ref.create(buildFunctionType(pyElement, callable, tspType))
  }

  private fun buildBuiltinFunctionType(pyElement: PyTypedElement, name: String?, tspType: PyreflyLsp4jServer.TspType): Ref<PyType?>? {
    if (name.isNullOrEmpty()) return null
    val callable = PyBuiltinCache.getInstance(pyElement).getByName(name) as? PyCallable ?: return null
    thisLogger().info("Pyrefly TSP: built PyFunctionType for builtin $name")
    return Ref.create(buildFunctionType(pyElement, callable, tspType))
  }

  private fun buildFunctionType(
    pyElement: PyTypedElement,
    callable: PyCallable,
    tspType: PyreflyLsp4jServer.TspType,
  ): PyCallableType {
    val psiParams = callable.parameterList.parameters
    val substituted = tspType.specializedTypes?.parameterTypes
    val parameters = psiParams.mapIndexed { i, p ->
      val resolved = substituted?.getOrNull(i)?.let { buildPyType(pyElement, it)?.get() }
      if (resolved != null) PyCallableParameterImpl.psi(p, resolved) else PyCallableParameterImpl.psi(p)
    }
    val returnTsp = tspType.specializedTypes?.returnType ?: tspType.returnType
    val returnType = returnTsp?.let { buildPyType(pyElement, it)?.get() }
    return PyCallableTypeImpl(null, PyCallableParameterListTypeImpl(parameters), returnType, callable, null)
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
    val moduleFile = pyElement.manager.findFile(virtualFile) as? PyFile ?: return null
    thisLogger().info("Pyrefly TSP: built PyModuleType for ${tspType.moduleName ?: moduleFile.name}")
    return Ref.create(PyModuleType(moduleFile))
  }

  private fun buildPyTypeVarType(pyElement: PyTypedElement, tspType: PyreflyLsp4jServer.TspType): Ref<PyType?>? {
    val declaration = tspType.declaration ?: return null
    val name = declaration.name
    if (name.isNullOrEmpty()) return null
    val defNode = declaration.node
    if (defNode == null || defNode.uri.isEmpty()) {
      thisLogger().info("Pyrefly TSP: built minimal PyTypeVarType for $name (no declaration node)")
      return Ref.create(PyTypeVarTypeImpl(name, null))
    }
    val target = findElement(defNode)
    if (target != null) {
      val context = TypeEvalContext.codeAnalysis(pyElement.project, target.containingFile)

      val typeParameter = PsiTreeUtil.getParentOfType(target, PyTypeParameter::class.java)
      if (typeParameter != null) {
        val pep695Type = PyTypingTypeProvider.getTypeParameterTypeFromTypeParameter(typeParameter, context)
        if (pep695Type is PyTypeVarType) {
          thisLogger().info("Pyrefly TSP: built PyTypeVarType for $name from PyTypeParameter at ${defNode.uri}:${defNode.range.start}")
          return Ref.create(pep695Type)
        }
      }

      val targetExpression = PsiTreeUtil.getParentOfType(target, PyTargetExpression::class.java)
      if (targetExpression != null) {
        val assignedType = context.getType(targetExpression)
        if (assignedType is PyTypeVarType) {
          thisLogger().info("Pyrefly TSP: built PyTypeVarType for $name from PyTargetExpression at ${defNode.uri}:${defNode.range.start}")
          return Ref.create(assignedType)
        }
      }
    }
    thisLogger().info("Pyrefly TSP: fell back to minimal PyTypeVarType for $name")
    return Ref.create(PyTypeVarTypeImpl(name, null))
  }

  private fun buildPyOverloadedType(pyElement: PyTypedElement, tspType: PyreflyLsp4jServer.TspType): Ref<PyType?>? {
    val overloads = tspType.overloads
    val implementationTsp = tspType.implementation
    if (overloads.isNullOrEmpty() && implementationTsp == null) return null
    val items = overloads?.mapNotNull { buildPyType(pyElement, it)?.get() as? PyCallableType } ?: emptyList()
    val impl = implementationTsp?.let { buildPyType(pyElement, it) }
    if (items.isEmpty() && impl == null) return null
    thisLogger().info("Pyrefly TSP: built PyOverloadType with ${items.size} overloads (impl=${impl?.get() != null})")
    return Ref.create(PyOverloadType(items, impl))
  }

  override fun requestTypes(pyTypedElements: @Unmodifiable Collection<PyTypedElement>): List<String?>? {
    val file = psiFile.virtualFile ?: return null
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
    val documentRequests = pyTypedElements.mapNotNull { psiElement ->
      getDocumentRequest(file, document, psiElement)
    }
    if (documentRequests.isEmpty()) return emptyList()

    val groupedRequests = LinkedHashMap<String, MutableList<IndexedValue<DocumentRequest>>>()
    documentRequests.forEachIndexed { index, request ->
      groupedRequests.computeIfAbsent(request.textDocument.uri) { mutableListOf() }.add(IndexedValue(index, request))
    }

    val contents = arrayOfNulls<String?>(documentRequests.size)
    for (group in groupedRequests.values) {
      val textDocument = group.first().value.textDocument
      val positions = group.map { it.value.position }

      val response = lspClient.sendRequestSync {
        (it as PyreflyLsp4jServer).provideType(textDocument, positions)
      }
      val groupContents = response?.contents?.map { it.value } ?: return null
      if (groupContents.size != group.size) return null

      group.zip(groupContents).forEach { (indexedRequest, content) ->
        contents[indexedRequest.index] = content
      }
    }
    return contents.toList()
  }

  override fun resolveStringType(element: PyTypedElement, stringType: String): Ref<PyType?>? {
    return resolveStringTypeImpl(element, stringType)
  }

  private fun PyreflyLsp4jServer.TspType.hasSourceLocation(): Boolean {
    if (declaration != null) {
      val nodeUri = declaration.node?.uri
      if (!nodeUri.isNullOrEmpty() || !declaration.uri.isNullOrEmpty()) return true
    }
    // Class/type references wrapped in a Function envelope keep their real declaration on returnType.
    return returnType?.hasSourceLocation() == true
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

  private data class DocumentRequest(val textDocument: TextDocumentIdentifier, val position: Position)

  private fun getDocumentRequest(
    file: VirtualFile,
    document: Document,
    psiElement: PyTypedElement,
  ): DocumentRequest? {
    if (psiElement is PsiFile) return null

    val textRange = psiElement.textRange
    if (textRange.isEmpty) return null

    val offsetDetector = PyreflyOffsetDetectorVisitor()
    psiElement.accept(offsetDetector)
    return getDocumentPosition(file, document, offsetDetector.offset)
  }

  private fun getDocumentPosition(
    file: VirtualFile,
    document: Document,
    offset: Int,
  ): DocumentRequest {
    val mappedPosition = (lspClient as? LspClientImpl)?.documentMapping?.getDocumentPosition(file, document, offset)
    if (mappedPosition != null) {
      return DocumentRequest(mappedPosition.document.id, mappedPosition.position)
    }
    return DocumentRequest(lspClient.getDocumentIdentifier(file), getLsp4jPosition(document, offset))
  }
}
