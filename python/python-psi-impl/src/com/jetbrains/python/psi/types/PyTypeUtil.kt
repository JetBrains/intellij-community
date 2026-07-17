/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.types

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.ast.PyAstFunction
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.inspections.PyInspectionMessages.CodifiedParam
import com.jetbrains.python.inspections.PyInspectionMessages.ProblemMessage
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyPossibleClassMember
import com.jetbrains.python.psi.PyPsiFacade
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.PyUtil.isObjectClass
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.types.PyRecursiveTypeVisitor.PyTypeTraverser
import com.jetbrains.python.psi.types.PyTypeChecker.GenericSubstitutions
import com.jetbrains.python.psi.types.PyTypeChecker.collectTypeSubstitutions
import com.jetbrains.python.psi.types.PyTypeChecker.convertToType
import com.jetbrains.python.psi.types.PyTypeChecker.findGenericDefinitionType
import com.jetbrains.python.psi.types.PyTypeChecker.hasGenerics
import com.jetbrains.python.psi.types.PyTypeChecker.match
import com.jetbrains.python.psi.types.PyTypeUtil.widenLiteralAndNumeric
import com.jetbrains.python.pyi.PyiUtil
import one.util.streamex.StreamEx
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.UnmodifiableView
import java.util.Collections
import java.util.stream.Collector
import java.util.stream.Collectors
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Tools and wrappers around [PyType] inheritors
 * 
 * @author Ilya.Kazakevich
 */
@ApiStatus.Internal
object PyTypeUtil {
  /**
   * Checks if two types are both assignable to each other, does not check if two types are equal
   */
  fun PyType?.isSameType(type2: PyType?, context: TypeEvalContext): Boolean {
    if ((this == null || type2 == null) && this !== type2) return false

    return match(this, type2, context)
           && match(type2, this, context)
  }

  /**
   * Checks if two types have a direct inheritance relationship, meaning one is a
   * subtype or supertype of the other.
   * 
   * 
   * This method handles [PyUnionType] by distributing the check across its
   * members. The types are considered subtype-related if the condition holds for
   * any pair of members.
   */
  fun PyType?.isSubtypeRelated(type2: PyType?, context: TypeEvalContext): Boolean {
    if (this is PyUnionType || this is PyUnsafeUnionType) {
      return this.members.any { it.isSubtypeRelated(type2, context) }
    }
    if (type2 is PyUnionType || type2 is PyUnsafeUnionType) {
      return type2.members.any { this.isSubtypeRelated(it, context) }
    }
    return match(this, type2, context)
           || match(type2, this, context)
  }

  /**
   * Returns members of certain type from [PyClassLikeType].
   */
  inline fun <reified T : PsiElement> PyClassLikeType.getMembers(
    inherited: Boolean,
    context: TypeEvalContext,
  ): List<T> = buildList {
    visitMembers({
                   if (it is T) {
                     add(it)
                   }
                   true
                 }, inherited, context)
  }

  /**
   * Returns members of certain type from [PyClassLikeType].
   */
  @JvmStatic
  fun <T : PsiElement> getMembersOfType(
    type: PyClassLikeType,
    expectedMemberType: Class<T>,
    inherited: Boolean,
    context: TypeEvalContext,
  ): List<T> = buildList {
    type.visitMembers({
                        if (expectedMemberType.isInstance(it)) {
                          @Suppress("UNCHECKED_CAST") // Already checked
                          add(it as T)
                        }
                        true
                      }, inherited, context)
  }


  /**
   * Search for data in dataholder or members of union recursively
   * 
   * @param this@findData start point
   * @param key  key to search
   * @param <T>  result tyoe
   * @return data or null if not found
  </T> */
  @JvmStatic
  fun <T> PyType.findData(key: Key<T>): T? {
    if (this is UserDataHolder) {
      return (this as UserDataHolder).getUserData(key)
    }
    if (this is PyUnionType) {
      for (memberType in this.members) {
        if (memberType == null) {
          continue
        }
        val result = memberType.findData(key)
        if (result != null) {
          return result
        }
      }
    }
    return null
  }

  @JvmStatic
  fun PsiElement.toPositionalContainerType(elementType: PyType?): PyTupleType? {
    if (elementType is PyUnpackedTupleTypeImpl) {
      return elementType.asTupleType(this)
    }
    else if (elementType is PyTypeVarTupleType) {
      return PyTupleType.create(this, listOf(elementType))
    }
    return PyTupleType.createHomogeneous(this, elementType)
  }

  @JvmStatic
  fun PsiElement.toKeywordContainerType(valueType: PyType?): PyClassType? {
    val builtinCache = PyBuiltinCache.getInstance(this)

    return builtinCache.dictType?.pyClass?.let {
      PyCollectionTypeImpl(it, false, listOf(builtinCache.strType, valueType))
    }
  }

  /**
   * Given a type creates a stream of all its members if it's a [PyCompositeType] or of only the type itself otherwise.
   *
   *
   * It lets a composite type (e.g. the union produced when a reference resolves to several declarations) be processed
   * uniformly with a non-composite one.
   */
  @Deprecated("Use the high level composite api instead `PyType.compositeX`")
  @JvmStatic
  fun PyType?.toStream(): StreamEx<PyType?> =
    if (this is PyCompositeType)
      StreamEx.of(this.members)
    else
      StreamEx.of(this)

  /**
   * Returns a sequence of all the union members if it's a union type, or of only the type itself otherwise.
   */
  @JvmStatic
  fun PyType?.asUnionSequence(): Sequence<PyType?> =
    if (this is PyUnionType || this is PyUnsafeUnionType)
      members.asSequence()
    else
      sequenceOf(this)

  @JvmStatic
  @Contract("null -> null; !null -> !null")
  fun PyType?.notNullToRef(): Ref<PyType>? =
    if (this == null) null else Ref(this)

  @JvmStatic
  @ApiStatus.Experimental
  fun Ref<out PyType?>?.derefOrUnknown(): PyType? =
    if (this == null) PyAnyType.unknown
    else this.get().also { PyAnyType.validate(it) }

  /**
   * Returns a collector that combines a stream of `Ref<PyType>` back into a single `Ref<PyType>`
   * using [PyUnionType.union].
   * 
   * @see .toUnion
   */
  @JvmStatic
  fun toUnionFromRef(): Collector<Ref<PyType?>?, *, Ref<PyType?>?> {
    return toUnionFromRef { type1, type2 -> PyUnionType.union(type1, type2) }
  }

  fun toUnsafeUnionFromRef(): Collector<Ref<PyType?>?, *, Ref<PyType?>?> {
    return toUnionFromRef { _, types ->
      PyUnsafeUnionType.unsafeUnion(types)
    }
  }

  @JvmStatic
  fun toUnionFromRef(streamSource: PyType?): Collector<Ref<PyType?>?, *, Ref<PyType?>?> {
    return if (streamSource is PyUnsafeUnionType)
      toUnionFromRef { _, types ->
        PyUnsafeUnionType.unsafeUnion(types)
      }
    else {
      toUnionFromRef { type1, type2 -> PyUnionType.union(type1, type2) }
    }
  }

  private fun toUnionFromRef(unionReduction: (PyType?, PyType?) -> PyType?): Collector<Ref<PyType?>?, *, Ref<PyType?>?> {
    return Collectors.reducing<Ref<PyType?>?>(null) { accType, hintType ->
      when {
        hintType == null -> accType
        accType == null -> hintType
        else -> Ref(unionReduction(accType.get(), hintType.get()))
      }
    }
  }

  /**
   * Returns a collector that combines a stream of types back into a single `PyType`
   * using [PyUnionType.union].
   * 
   * 
   * Note that it's different from using `foldLeft(PyUnionType::union)` because the latter returns `Optional<PyType>`,
   * and it doesn't support `null` values throwing `NullPointerException` if the final result of
   * [PyUnionType.union] was `null`.
   * 
   * 
   * This method doesn't distinguish between an empty stream and a stream containing only `null` returning `null` for both cases.
   * 
   * @see .toUnionFromRef
   */
  @JvmStatic
  fun toUnion(): Collector<PyType?, *, PyType?> {
    return Collectors.collectingAndThen(
      Collectors.toList()
    ) { members -> PyUnionType.union(members) }
  }

  @ApiStatus.Experimental
  fun toUnsafeUnion(): Collector<PyType?, *, PyType?> {
    return Collectors.collectingAndThen(
      Collectors.toList()
    ) { PyUnsafeUnionType.unsafeUnion() }
  }

  @ApiStatus.Experimental
  fun toIntersection(): Collector<PyType?, *, PyType?> {
    return Collectors.collectingAndThen(
      Collectors.toList()
    ) { PyIntersectionType.intersection() }
  }

  @JvmStatic
  fun toUnion(streamSource: PyType?): Collector<PyType?, *, PyType?> {
    return if (streamSource is PyUnsafeUnionType)
      toUnion { PyUnsafeUnionType.unsafeUnion(it) }
    else toUnion { members ->
      PyUnionType.union(members)
    }
  }

  val PyType?.compositeComponents: List<PyType?>
    @ApiStatus.Experimental
    get() = if (this is PyCompositeType) members.toList() else listOf(this)

  val PyType?.compositeComponentSequence: Sequence<PyType?>
    @ApiStatus.Experimental
    get() = if (this is PyCompositeType) members.asSequence() else sequenceOf(this)

  /**
   * Decomposes [this] into the list of its members (or a single-element list for a non-composite type),
   * hands that list to [transform], and reassembles the result into a composite type *of the same kind*
   * ([PyUnionType], [PyUnsafeUnionType] or [PyIntersectionType]); a non-composite type is rebuilt as a union,
   * which collapses a single remaining member back to itself.
   *
   * It lets one uniformly deconstruct a composite type, process its members and build it back without branching
   * on the concrete composite kind:
   * ```
   * val transformed = someType.compositeTransform { members ->
   *   members.map { ... }.filter { ... }.mapNotNull { ... }
   * }
   * ```
   *
   * If [transform] returns an empty list, the result is the identity element of the composite kind:
   * `Never` for a union or an unsafe union (bottom), and `object` for an intersection (top). The `object` type is
   * resolved against [this]'s original members and degrades to [PyAnyType.unknown] only for an anchorless intersection
   * (one made solely of `Any`/`None`).
   *
   * @see compositeMap
   * @see compositeMatchesAsSubtype
   * @see compositeMatchesAsSupertype
   */
  @ApiStatus.Experimental
  fun PyType?.compositeTransform(transform: (List<PyType?>) -> List<PyType?>): PyType? =
    rebuildLike(transform(compositeComponents))

  /**
   * Applies [mapper] to every leaf member of [this] composite type and reassembles the results into composite types
   * of the same kinds; a non-composite type is passed to [mapper] directly.
   *
   * Recurses through nested composites ([PyUnionType], [PyUnsafeUnionType], [PyIntersectionType]) so that [mapper]
   * always receives a non-composite type, e.g. `Union[Intersection[A, B], C]` is mapped to
   * `Union[Intersection[f(A), f(B)], f(C)]`. Use [compositeTransform] instead when you need the flat list of the
   * *direct* members without recursion.
   *
   * @see compositeTransform
   * @see PyUnionType.map
   */
  @ApiStatus.Experimental
  fun PyType?.compositeMap(mapper: (PyType?) -> PyType?): PyType? =
    if (this is PyCompositeType) compositeTransform { members -> members.map { it.compositeMap(mapper) } }
    else mapper(this)

  /**
   * Tests [predicate] against the members of [this] composite type with the quantifier that decides whether the
   * *whole composite is a subtype* of some other type: since `Union[Ts] <: T` iff every `Ti <: T`, *all* members of a
   * [PyUnionType] must match; since `Intersection[Ts] <: T` (and `UnsafeUnion[Ts] <: T`) iff some `Ti <: T`, *any*
   * member of a [PyIntersectionType] or a [PyUnsafeUnionType] is enough. For a non-composite type [predicate] is
   * applied directly.
   *
   * [predicate] is expected to test the member on the subtype side, e.g. `compositeMatchesAsSubtype { match(it, target) }`.
   *
   * @see compositeMatchesAsSupertype
   */
  @ApiStatus.Experimental
  fun PyType?.compositeMatchesAsSubtype(predicate: (PyType?) -> Boolean): Boolean =
    when (this) {
      is PyUnionType -> members.all(predicate)
      is PyIntersectionType, is PyUnsafeUnionType -> members.any(predicate)
      else -> predicate(this)
    }

  /**
   * The mirror of [compositeMatchesAsSubtype]: the quantifier that decides whether some other type is a *subtype of the whole
   * composite*. Since `T <: Union[Ts]` iff some `T <: Ti`, *any* member of a [PyUnionType] (or a [PyUnsafeUnionType])
   * suffices; since `T <: Intersection[Ts]` iff `T <: Ti` for every member, *all* members of a [PyIntersectionType]
   * must match. For a non-composite type [predicate] is applied directly.
   *
   * [predicate] is expected to test the member on the supertype side, e.g. `compositeMatchesAsSupertype { match(target, it) }`.
   *
   * @see compositeMatchesAsSubtype
   */
  @ApiStatus.Experimental
  fun PyType?.compositeMatchesAsSupertype(predicate: (PyType?) -> Boolean): Boolean =
    when (this) {
      is PyUnionType, is PyUnsafeUnionType -> members.any(predicate)
      is PyIntersectionType -> members.all(predicate)
      else -> predicate(this)
    }

  /**
   * Rebuilds a composite type of the same kind as [this] from [members], folding an empty [members] to the identity
   * element of that kind: `Never` for unions/unsafe unions, `Unknown` for intersections.
   */
  private fun PyType?.rebuildLike(members: List<PyType?>): PyType? =
    when (this) {
      // TODO: use PyTopType when it is introduced PY-90942
      is PyIntersectionType -> if (members.isEmpty()) PyAnyType.unknown else PyIntersectionType.intersection(members)
      is PyUnsafeUnionType -> if (members.isEmpty()) PyNeverType.NEVER else PyUnsafeUnionType.unsafeUnion(members)
      else -> PyUnionType.unionOrNever(members)
    }

  private fun toUnion(unionFactory: (List<PyType?>) -> PyType?): Collector<PyType?, *, PyType?> {
    return Collectors.collectingAndThen(Collectors.toList(), unionFactory)
  }

  @JvmStatic
  fun PyType?.isDict(): Boolean {
    return this is PyClassType && this.isParameterized && "dict" == this.name
  }

  @JvmStatic
  @ApiStatus.Internal
  fun PyType?.convertToType(
    superTypeName: String,
    anchor: PsiElement,
    context: TypeEvalContext,
  ): PyType? {
    val superClass = PyPsiFacade.getInstance(anchor.project).createClassByQName(superTypeName, anchor) ?: return null
    val superClassType = findGenericDefinitionType(superClass, context) ?: PyClassTypeImpl(superClass, false)
    return convertToType(this, superClassType, context)
  }

  @JvmStatic
  fun PyType?.inheritsAny(context: TypeEvalContext): Boolean {
    return this is PyClassLikeType && this.getAncestorTypes(context).contains(null)
  }

  /**
   * Collects a set of types that participate in the textual type hint representation of `type`.
   * The returned set preserves a stable DFS order and is unmodifiable.
   */
  @JvmStatic
  fun PyType?.collectTypeComponents(
    context: TypeEvalContext,
  ): @UnmodifiableView Set<PyType?> {
    val result: MutableSet<PyType?> = LinkedHashSet()

    PyRecursiveTypeVisitor.traverse(this, context, object : PyTypeTraverser() {
      override fun visitPyType(pyType: PyType): PyRecursiveTypeVisitor.Traversal {
        result.add(pyType)
        return super.visitPyType(pyType)
      }

      override fun visitPyLiteralType(literalType: PyLiteralType): PyRecursiveTypeVisitor.Traversal {
        val literalClassType = literalType.pyClass.getType(context)
        if (literalClassType != null) {
          // Adds eg. signal.Handler when the given type was Literal[Handlers.SIG_DFL]
          result.add(literalClassType)
        }
        return super.visitPyLiteralType(literalType)
      }

      override fun visitUnknownType(): PyRecursiveTypeVisitor.Traversal {
        result.add(null) // add Any type
        return super.visitUnknownType()
      }
    })

    return Collections.unmodifiableSet(result)
  }

  /**
   * Extracts string literal values from a `tuple[Literal["a"], Literal["b"], ...]` type.
   * Returns `null` if the type is not a non-homogeneous tuple of string literals.
   * 
   * @see createTupleOfLiteralStringsType
   */
  @JvmStatic
  @ApiStatus.Internal
  fun extractStringLiteralsFromTupleType(type: PyType?): List<String>? {
    if (type !is PyTupleType || type.isHomogeneous) return null
    return buildList {
      for (elementType in type.elementTypes) {
        if (elementType !is PyLiteralType) return null
        val str = elementType.stringValue ?: return null
        add(str)
      }
    }
  }

  /**
   * Creates a `tuple[Literal["name1"], Literal["name2"], ...]` type from a list of strings.
   * Useful for creating types for synthetic members (e.g. `__match_args__`, `__slots__` in a dataclasses).
   * 
   * @see extractStringLiteralsFromTupleType
   */
  @ApiStatus.Internal
  fun createTupleOfLiteralStringsType(anchor: PsiElement, fieldNames: List<String>): PyTupleType? {
    val literalTypes = fieldNames.mapNotNull { PyLiteralType.stringLiteral(anchor, it) }
    return PyTupleType.create(anchor, literalTypes)
  }

  @JvmStatic
  fun widenLiteralAndNumeric(type: PyType?): PyType? {
    return type.widenTupleLiterals()
      .let { PyLiteralType.upcastLiteralToClass(it) }
      .let { PyNumericTowerUtil.enrich(it) }
  }

  @ApiStatus.Internal
  fun mapCallableType(functionType: PyType?, mapper: (PyCallableType) -> PyCallableType?): PyType? {
    return when (functionType) {
      is PyClassLikeType -> functionType
      is PyCallableType -> mapper(functionType)
      is PyOverloadType -> functionType.map { if (it == null) null else mapper(it) }
      else -> functionType
    }
  }

  @ApiStatus.Internal
  @JvmStatic
  fun getCallableItems(functionType: PyType?): Sequence<PyCallableType> {
    return when (functionType) {
      is PyClassLikeType -> emptySequence()
      is PyCallableType -> sequenceOf(functionType)
      is PyOverloadType -> functionType.items.filterNotNull().asSequence()
      else -> emptySequence()
    }
  }

  @ApiStatus.Internal
  @JvmStatic
  fun getContainingClass(resolveResults: List<@JvmWildcard RatedResolveResult>): PyClass? {
    return resolveResults.asSequence()
      .map { it.element }
      .filterIsInstance<PyPossibleClassMember>()
      .firstNotNullOfOrNull { it.containingClass }
  }

  @ApiStatus.Internal
  @JvmStatic
  @JvmOverloads
  fun getTypeOfBoundMember(
    classType: PyClassType,
    memberResolveResults: List<@JvmWildcard RatedResolveResult>,
    context: TypeEvalContext,
    errors: MutableList<ProblemMessage>? = null,
  ): PyType? {
    val memberType = getTypeOfMember(memberResolveResults, context)
    val specializedMemberType = specializeMemberType(classType, classType, memberType, context)
    val memberOwner = getContainingClass(memberResolveResults)
    return bindFunction(classType, specializedMemberType, memberOwner, context, errors)
  }

  @ApiStatus.Internal
  @JvmStatic
  fun getTypeOfMember(
    memberResolveResults: List<@JvmWildcard RatedResolveResult>,
    context: TypeEvalContext,
  ): PyType? {
    val resolvedElements = memberResolveResults.mapNotNull { it.element as? PyTypedElement }
    if (resolvedElements.isEmpty()) return PyAnyType.unknown

    // Element with a declared type takes precedence.
    val elementsWithDeclaredType = resolvedElements
      .filter {
        it is PyClass ||
        it is PyFunction ||
        it is PyTargetExpression && (it.annotationValue != null || it.typeCommentAnnotation != null)
      }
    val elements = elementsWithDeclaredType.ifEmpty { resolvedElements }

    val last = elements.last()
    val lastType = context.getType(last)
    if (lastType !is PyFunctionType) {
      val memberType = if (last is PyTargetExpression && last.isQualified && !PyTypingTypeProvider.isFinal(last, context)) {
        PyLiteralType.upcastLiteralToClass(lastType)
      }
      else {
        lastType
      }
      if (elementsWithDeclaredType.isEmpty() && memberType.isNoneType) {
        return PyUnsafeUnionType.unsafeUnion(memberType, PyAnyType.unknown)
      }
      return memberType
    }

    val overloads = mutableListOf<PyCallableType?>()
    var impl: Ref<PyType?>? = null
    if (PyiUtil.isOverload(last, context)) {
      overloads.add(lastType as? PyCallableType)
    }
    else {
      impl = Ref.create(lastType)
    }
    for (i in elements.lastIndex - 1 downTo 0) {
      val el = elements[i]
      if (!PyiUtil.isOverload(el, context)) break
      overloads.add(context.getType(el) as? PyCallableType)
    }
    if (overloads.isEmpty()) return lastType

    overloads.reverse()
    return PyOverloadType(overloads, impl)
  }

  @ApiStatus.Internal
  @JvmStatic
  fun specializeMemberType(
    classType: PyClassType,
    selfType: PyInstantiableType<*>,
    memberType: PyType?,
    context: TypeEvalContext,
  ): PyType? {
    return if (memberType.hasGenerics(context)) {
      val substitutions = collectTypeSubstitutions(classType, context)
      substitutions.qualifierType = selfType
      PyTypeChecker.substitute(memberType, substitutions, context)
    }
    else memberType
  }

  /**
   * If [memberType] is an instance or class method, returns its specialized bound type,
   * or [PyAnyType.unknown] if binding failed (the type of its first parameter does not match [classType]);
   * otherwise returns [memberType] as is.
   */
  @ApiStatus.Internal
  @JvmStatic
  fun bindFunction(
    classType: PyInstantiableType<*>,
    memberType: PyType?,
    memberOwner: PyClass?,
    context: TypeEvalContext,
    errors: MutableList<ProblemMessage>?
  ): PyType? {
    val signatures = getCallableItems(memberType).toList()
    if (signatures.isEmpty()) return memberType

    val isStaticMethod = signatures.all { it.modifier == PyAstFunction.Modifier.STATICMETHOD }
    if (isStaticMethod) return memberType

    val isClassMethod = signatures.all { it.modifier == PyAstFunction.Modifier.CLASSMETHOD }

    var shouldBind = false
    if (!classType.isDefinition() || isClassMethod) {
      shouldBind = true
    }
    else if (memberOwner != null && classType is PyClassType) {
      // If a member's owner is not an ancestor of the class,
      // the member must have come from the metaclass -> bind it to the class.
      // TODO: This condition should be computed by the calling code when resolving `memberType`
      shouldBind = !classType.pyClass.isSubclass(memberOwner, context)
    }

    if (shouldBind) {
      val selfType = if (isClassMethod) classType.toClass() else classType
      val boundMethodType = mapCallableType(memberType) { bindFunction(it, selfType, context)?.boundMethodType }
      if (boundMethodType == null) {
        errors?.add(methodBindingErrorMessage(selfType, memberType, context))
        return PyAnyType.unknown
      }
      return boundMethodType
    }

    return memberType
  }

  @ApiStatus.Internal
  @JvmStatic
  fun methodBindingErrorMessage(
    selfType: PyType,
    memberType: PyType?,
    context: TypeEvalContext,
  ): ProblemMessage {
    val methodType = if (memberType is PyOverloadType) memberType.items.firstOrNull() else memberType
    val callable = (methodType as? PyCallableType)?.callable
    // Any element in a Python file works as the link-resolution scope; the type names link to global declarations.
    val anchor: PsiElement? = callable ?: (selfType as? PyClassType)?.pyClass
    val selfTypeParam = selfType.toCodeSpan(anchor, context)
    val methodTypeParam = methodType.toCodeSpan(anchor, context)
    val methodName = callable?.name?.let { name ->
      val containingClass = callable.asMethod()?.containingClass?.name
      if (containingClass != null) "$containingClass.$name" else name
    }
    return if (methodName != null) {
      PyPsiBundle.problemMessage("INSP.type.checker.invalid.self.argument.to.named.method.with.type",
                                 selfTypeParam, methodName, methodTypeParam)
    }
    else {
      PyPsiBundle.problemMessage("INSP.type.checker.invalid.self.argument.to.method.with.type", selfTypeParam, methodTypeParam)
    }
  }

  /**
   * Renders [this] as a code span for an inspection message: a [CodifiedParam] with clickable type links when an
   * [anchor] is available, otherwise the plain type name (the message template still renders it as a code span).
   */
  private fun PyType?.toCodeSpan(anchor: PsiElement?, context: TypeEvalContext): Any =
    if (anchor != null) CodifiedParam.ofType(this, anchor, context)
    else PythonDocumentationProvider.getTypeName(this, context)

  @ApiStatus.Internal
  @JvmStatic
  fun bindFunction(callableType: PyCallableType, selfType: PyType, context: TypeEvalContext): FunctionBindingResult? {
    if (callableType.getParametersType(context) == null) {
      // `typing.Callable[..., R]` - treat as `(*args, **kwargs)`.
      return FunctionBindingResult(callableType, PyAnyType.any)
    }
    val firstParam = callableType.getParameters(context)?.firstOrNull()
    if (firstParam != null && !firstParam.isPositionOnlySeparator && !firstParam.isKeywordOnlySeparator) {
      val firstParamType = firstParam.getArgumentType(context)
      val substitutions = GenericSubstitutions()
      substitutions.qualifierType = selfType
      if (firstParamType !is PySelfType) {
        if (!match(firstParamType, selfType, context, substitutions)) {
          return null
        }
      }
      val specializedCallableType = PyTypeChecker.substitute(callableType, substitutions, context) as PyCallableType
      val specializedFirstParamType = specializedCallableType.getParameters(context)!!.first().getArgumentType(context)
      return FunctionBindingResult(specializedCallableType.dropFirstParam(context), specializedFirstParamType)
    }
    return null
  }

  @ApiStatus.Internal
  data class FunctionBindingResult(val boundMethodType: PyCallableType, val specializedFirstParamType: PyType?)

  private fun PyCallableType.dropFirstParam(context: TypeEvalContext): PyCallableType {
    if (this is PyFunctionType) {
      val params = getParameters(context)
      if (!params.isNullOrEmpty()) {
        val firstParam = params.first()
        if (!firstParam.isPositionalContainer && !firstParam.isKeywordContainer) {
          return PyFunctionTypeImpl(callable, params.drop(1))
        }
      }
      return this
    }
    else {
      val parametersType = getParametersType(context)
      if (parametersType is PyCallableParameterListType) {
        val params = parametersType.parameters
        if (params.isNotEmpty()) {
          val firstParam = params.first()
          if (!firstParam.isPositionalContainer && !firstParam.isKeywordContainer) {
            return PyCallableTypeImpl(getTypeParameters(context),
                                      PyCallableParameterListTypeImpl(params.drop(1)),
                                      getReturnType(context),
                                      callable,
                                      modifier
            )
          }
        }
      }
      return this
    }
  }
}

@OptIn(ExperimentalContracts::class)
val PyType?.isAnyOrUnknown: Boolean
  @Contract("null -> true")
  get() {
  contract {
    returns(true) implies (this@isAnyOrUnknown is PyAnyType?)
    returns(false) implies (this@isAnyOrUnknown is PyType)
  }

  PyAnyType.validate(this)
  return if (PyAnyType.isEnabled) this is PyAnyType else this == null
}

@OptIn(ExperimentalContracts::class)
val PyType?.isAny: Boolean
  @Contract("null -> true")
  get() {
    contract {
      returns(true) implies (this@isAny is PyAnyType.Any?)
      returns(false) implies (this@isAny is PyType)
    }
    PyAnyType.validate(this)
    return if (PyAnyType.isEnabled) this is PyAnyType.Any else this == null
  }

@OptIn(ExperimentalContracts::class)
val PyType?.isUnknown: Boolean
  @Contract("null -> true")
  get() {
    contract {
      returns(true) implies (this@isUnknown is PyAnyType.Unknown?)
      returns(false) implies (this@isUnknown is PyType)
    }

    PyAnyType.validate(this)
    return if (PyAnyType.isEnabled) this is PyAnyType.Unknown else this == null
  }

@OptIn(ExperimentalContracts::class)
val PyType?.isObject: Boolean
  get() {
    contract {
      returns(true) implies (this@isObject is PyClassType)
    }

    return this is PyClassType && isObjectClass(this.pyClass)
  }

@ApiStatus.Internal
fun PyExpression.getLiteralType(context: TypeEvalContext): PyType? =
  PyLiteralType.getLiteralType(this, context)

/**
 * Widens literal types within a tuple type.
 * When a tuple appears nested in a non-tuple container type (e.g., `list[tuple[Literal[1], Literal["a"]]]`),
 * its literal element types should be widened to their base types (e.g., `list[tuple[int, str]]`).
 */
@ApiStatus.Experimental
fun PyType?.widenTupleLiterals(): PyType? {
  if (this !is PyTupleType || this is PyNamedTupleType) return this
  val widenedElements = this.elementTypes.map { widenLiteralAndNumeric(it) }
  return PyTupleType(this.pyClass, widenedElements, this.isHomogeneous)
}
