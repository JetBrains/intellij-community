// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.intellij.tools.apiDump

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.metadata.jvm.JvmFieldSignature
import kotlinx.validation.api.*
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.walk

val emptyApiIndex: ApiIndex = ApiIndex(
  persistentHashMapOf(),
  persistentHashMapOf(),
)

class ApiIndex private constructor(
  private val packages: PersistentMap<String, ApiAnnotations>,
  internal val classes: PersistentMap<String, ClassBinarySignature>,
) {

  operator fun plus(other: ApiIndex): ApiIndex {
    return ApiIndex(
      this.packages.putAll(other.packages),
      this.classes.putAll(other.classes),
    )
  }

  internal fun packageAnnotations(packageName: String): ApiAnnotations {
    return packages[packageName]
           ?: unannotated
  }

  /**
   * @return
   * - `null` if [className] is unknown, for example, a library class;
   * - `false` if known but excluded from the API, for example, `@Internal` class;
   * - `true` if known and actually a part of the API.
   */
  fun isPublicOrUnknown(className: String): Boolean? {
    return resolveClass(className)?.isEffectivelyPublic
  }

  internal fun resolveClass(className: String): ClassBinarySignature? {
    return classes[className]
  }

  internal fun discoverPackages(packages: Map<String, ApiAnnotations>): ApiIndex {
    val builder = this.packages.builder()
    for ((packageName, packageAnnotations) in packages) {
      check(this.packages[packageName] == null)
      builder[packageName] = packageAnnotations
    }
    return ApiIndex(
      packages = builder.toPersistentHashMap(),
      classes,
    )
  }

  internal fun discoverClass(signature: ClassBinarySignature): ApiIndex {
    val className = signature.name
    check(classes[className] == null) {
      "Class already discovered $className"
    }
    return ApiIndex(
      packages,
      classes = classes.put(className, signature),
    )
  }
}

class API internal constructor(
  val index: ApiIndex,
  signatures: List<ClassBinarySignature>,
) {

  val publicApi: List<ApiClass> by lazy {
    publicApi(index, signatures)
  }

  private val stableAndExperimentalApi: Pair<List<ApiClass>, List<ApiClass>> by lazy {
    stableAndExperimentalApi(publicApi)
  }

  val stableApi: List<ApiClass> get() = stableAndExperimentalApi.first

  val experimentalApi: List<ApiClass> get() = stableAndExperimentalApi.second
}

/**
 * @return a list of classes (with members) in [root], which are considered API
 */
fun api(index: ApiIndex, root: Path): API {
  @Suppress("NAME_SHADOWING")
  var index = index
  val classFilePaths: Sequence<Path> = classFilePaths(root)

  val packages: Map<String, ApiAnnotations> = classFilePaths.packages()
  index = index.discoverPackages(packages)

  val signatures: List<ClassBinarySignature> = classFilePaths
    .map { it.inputStream() }
    .loadApiFromJvmClasses()
    .map { it.removeSyntheticBridges() }
    .map { it.removeToString() }
    .map { signature ->
      signature.handleAnnotationsAndVisibility(index).also {
        /**
         * Class has to be saved to the [ApiIndex.classes] map in the same iteration
         * because the next [handleAnnotationsAndVisibility] call relies on it
         * to resolve the outer class name.
         */
        index = index.discoverClass(it)
      }
    }
  return API(index, signatures)
}

/**
 * - Pushes [isEffectivelyPublic] from class to inner class.
 * - Pushes [@Internal][org.jetbrains.annotations.ApiStatus.Internal] and [@Experimental][org.jetbrains.annotations.ApiStatus.Experimental]
 * from package to class and from class to inner classes.
 */
private fun ClassBinarySignature.handleAnnotationsAndVisibility(index: ApiIndex): ClassBinarySignature {
  if (!isEffectivelyPublic) {
    return this
  }
  var signature = this
  val outerName = outerName
  if (outerName != null) {
    val outerClass = index.resolveClass(outerName) ?: error("Outer class $outerName is unknown")
    signature = signature.annotate(outerClass.annotations.apiAnnotations())
    if (!outerClass.isEffectivelyPublic || access.isProtected && outerClass.isEffectivelyFinal()) {
      signature = signature.copy(isEffectivelyPublic = false)
    }
  }
  else {
    val packageAnnotations = index.packageAnnotations(name.packageName())
    signature = signature.annotate(packageAnnotations)
  }
  if (signature.annotations.apiAnnotations().isInternal) {
    signature = signature.copy(isEffectivelyPublic = false)
  }
  return signature
}

private fun companionAnnotations(
  index: ApiIndex,
  containingClassSignature: ClassBinarySignature,
  memberSignature: MemberBinarySignature,
): ApiAnnotations? {
  val access = memberSignature.access
  if (!access.isStatic || !access.isFinal || memberSignature.name != "Companion") {
    return null
  }
  if (memberSignature.jvmMember !is JvmFieldSignature) {
    return null
  }
  val type = Type.getType(memberSignature.desc)
  if (type.sort != Type.OBJECT) {
    return null
  }
  val companionSignature = index.resolveClass(type.internalName)
  if (companionSignature?.outerName != containingClassSignature.name) {
    return null
  }
  // this is a `static final ContainingClassType Companion` field
  return ApiAnnotations(
    isInternal = companionSignature.annotations.isInternal(),
    isExperimental = companionSignature.annotations.isExperimental(),
  )
}

/**
 * @see kotlinx.validation.api.filterOutNonPublic
 */
private fun ClassBinarySignature.removePrivateSupertypes(index: ApiIndex): ClassBinarySignature {
  val (publicSupertypeNames, privateSupertypes) = expandPrivateSupertypes(index::resolveClass)
  if (privateSupertypes.isEmpty()) {
    return this
  }
  val isFinal = isEffectivelyFinal()
  val signatures = memberSignatures.mapTo(HashSet()) { it.jvmMember }
  val inheritedSignatures = sequence {
    for (supertype in privateSupertypes) {
      if (supertype.annotations.isInternal()) {
        // Members of an `@Internal` class are also effectively `@Internal`.
        continue
      }
      for (member in supertype.memberSignatures) {
        if (member.name == "<init>") {
          continue
        }
        if (isFinal && member.access.isProtected) {
          continue
        }
        if (!signatures.add(member.jvmMember)) {
          // don't inherit if already exists
          continue
        }
        this.yield(member)
      }
    }
  }
  return copy(
    memberSignatures = memberSignatures + inheritedSignatures,
    supertypes = publicSupertypeNames,
  )
}

private fun publicApi(index: ApiIndex, classSignatures: List<ClassBinarySignature>): List<ApiClass> {
  val publicSignatures: List<ClassBinarySignature> = classSignatures
    .filter { it.isEffectivelyPublic }
    .map { it.removePrivateSupertypes(index) }

  val result = ArrayList<ApiClass>()
  for (signature in publicSignatures) {
    val className = signature.name
    val isFinal = signature.isEffectivelyFinal()
    val members = signature.memberSignatures
      .sortedWith(MEMBER_SORT_ORDER)
      .mapNotNull { memberSignature ->
        val companionAnnotations = companionAnnotations(index, signature, memberSignature) ?: unannotated
        if (memberSignature.annotations.isInternal() || companionAnnotations.isInternal) {
          return@mapNotNull null
        }
        if (memberSignature.isConstructorAccessor()) {
          return@mapNotNull null
        }
        if (isFinal && memberSignature.access.isProtected) {
          return@mapNotNull null
        }
        ApiMember(
          ApiRef(memberSignature.name, memberSignature.desc),
          ApiFlags(
            memberSignature.access.access,
            annotationExperimental = memberSignature.annotations.isExperimental() || companionAnnotations.isExperimental,
            annotationNonExtendable = memberSignature.annotations.isNonExtendable(),
          ),
        )
      }
    if (members.isEmpty() && signature.isNotUsedWhenEmpty) {
      continue
    }
    result += ApiClass(
      className,
      flags = ApiFlags(
        signature.access.access,
        signature.annotations.isExperimental(),
        signature.annotations.isNonExtendable(),
      ),
      supers = signature.supertypes,
      members,
    )
  }
  return result
}

private fun stableAndExperimentalApi(classSignatures: List<ApiClass>): Pair<List<ApiClass>, List<ApiClass>> {
  val stableClassSignatures = ArrayList<ApiClass>()
  val experimentalClassSignatures = ArrayList<ApiClass>()
  for (classSignature in classSignatures) {
    if (classSignature.flags.annotationExperimental) {
      // the whole class is experimental
      experimentalClassSignatures.add(classSignature)
      continue
    }
    val stableMembers = ArrayList<ApiMember>()
    val experimentalMembers = ArrayList<ApiMember>()
    for (member in classSignature.members) {
      val memberList = if (member.flags.annotationExperimental) {
        experimentalMembers
      }
      else {
        stableMembers
      }
      memberList.add(member)
    }
    if (experimentalMembers.isEmpty()) {
      // a stable class has only stable members
      stableClassSignatures.add(classSignature)
      continue
    }
    // keep only experimental members
    experimentalClassSignatures.add(classSignature.copy(members = experimentalMembers))

    // keep only stable members but also keep the signature in the stable list even if all members are experimental
    stableClassSignatures.add(classSignature.copy(members = stableMembers))
  }
  return Pair(stableClassSignatures, experimentalClassSignatures)
}

@OptIn(ExperimentalPathApi::class)
private fun classFilePaths(classRoot: Path): Sequence<Path> {
  return classRoot
    .walk()
    .filter { path ->
      path.name.endsWith(".class") &&
      !classRoot.relativize(path).startsWith("META-INF/")
    }
}

internal data class ApiAnnotations(val isInternal: Boolean, val isExperimental: Boolean) {

  operator fun plus(other: ApiAnnotations): ApiAnnotations {
    if (other == unannotated && this == unannotated) {
      return unannotated
    }
    return ApiAnnotations(
      isInternal || other.isInternal,
      isExperimental || other.isExperimental,
    )
  }
}

private val unannotated = ApiAnnotations(false, false)

/**
 * @receiver sequence of paths to class files
 */
private fun Sequence<Path>.packages(): Map<String, ApiAnnotations> {
  val packages = HashMap<String, ApiAnnotations>()
  for (path in this) {
    if (!path.endsWith("package-info.class")) {
      continue
    }
    val node = readClass(path)
    packages[node.name.packageName()] = ApiAnnotations(
      isInternal = node.invisibleAnnotations.isInternal(),
      isExperimental = node.invisibleAnnotations.isExperimental(),
    )
  }
  return packages
}

private const val API_STATUS_INTERNAL_DESCRIPTOR = "Lorg/jetbrains/annotations/ApiStatus\$Internal;"
private const val API_STATUS_EXPERIMENTAL_DESCRIPTOR = "Lorg/jetbrains/annotations/ApiStatus\$Experimental;"
private const val API_STATUS_NON_EXTENDABLE = "Lorg/jetbrains/annotations/ApiStatus\$NonExtendable;"

private fun List<AnnotationNode>.apiAnnotations(): ApiAnnotations {
  var isInternal = false
  var isExperimental = false
  for (node in this) {
    if (node.desc == API_STATUS_INTERNAL_DESCRIPTOR) {
      isInternal = true
    }
    if (node.desc == API_STATUS_EXPERIMENTAL_DESCRIPTOR) {
      isExperimental = true
    }
  }
  if (isInternal || isExperimental) {
    return ApiAnnotations(isInternal, isExperimental)
  }
  else {
    return unannotated
  }
}

private fun ClassBinarySignature.isEffectivelyFinal(): Boolean {
  return access.isFinal || annotations.isNonExtendable()
}

private fun List<AnnotationNode>?.isInternal(): Boolean {
  return hasAnnotation(API_STATUS_INTERNAL_DESCRIPTOR)
}

private fun List<AnnotationNode>?.isExperimental(): Boolean {
  return hasAnnotation(API_STATUS_EXPERIMENTAL_DESCRIPTOR)
}

private fun List<AnnotationNode>?.isNonExtendable(): Boolean {
  return hasAnnotation(API_STATUS_NON_EXTENDABLE)
}

private typealias ClassResolver = (String) -> ClassBinarySignature?

private fun ClassBinarySignature.removeSyntheticBridges(): ClassBinarySignature {
  val withoutBridges = memberSignatures.filterNot {
    it is MethodBinarySignature && it.isSyntheticBridge()
  }
  if (withoutBridges.size == memberSignatures.size) {
    return this
  }
  else {
    return copy(memberSignatures = withoutBridges)
  }
}

private fun MethodBinarySignature.isSyntheticBridge(): Boolean {
  return access.access.let { flags ->
    !flags.isSet(Opcodes.ACC_STATIC)
    && flags.isSet(Opcodes.ACC_BRIDGE)
    && flags.isSet(Opcodes.ACC_SYNTHETIC)
  }
}

private fun ClassBinarySignature.removeToString(): ClassBinarySignature {
  val withoutToString = memberSignatures.filterNot { signature ->
    signature is MethodBinarySignature
    && !signature.access.access.isSet(Opcodes.ACC_ABSTRACT)
    && signature.jvmMember.let { member ->
      member.name == "toString" && member.desc == "()Ljava/lang/String;"
    }
  }
  if (withoutToString.size == memberSignatures.size) {
    return this
  }
  else {
    return copy(memberSignatures = withoutToString)
  }
}

private fun ClassBinarySignature.annotate(outerApiAnnotations: ApiAnnotations): ClassBinarySignature {
  val classApiAnnotations = annotations.apiAnnotations()
  val internalMissing = outerApiAnnotations.isInternal && !classApiAnnotations.isInternal
  val experimentalMissing = outerApiAnnotations.isExperimental && !classApiAnnotations.isExperimental
  if (!internalMissing && !experimentalMissing) {
    return this
  }
  val result = ArrayList(annotations)
  if (internalMissing) {
    result.add(AnnotationNode(API_STATUS_INTERNAL_DESCRIPTOR))
  }
  if (experimentalMissing) {
    result.add(AnnotationNode(API_STATUS_EXPERIMENTAL_DESCRIPTOR))
  }
  return copy(annotations = result)
}

private data class ExpandedSupertypes(
  val publicSupertypeNames: List<String>,
  val privateSupertypes: List<ClassBinarySignature>,
)

/**
 * Recursively traverses the hierarchy of [this] and replaces private supertypes
 * with their supertypes, until only public supertypes remain.
 *
 * @return a [list of new supertypes][ExpandedSupertypes.publicSupertypeNames] and
 * a [list of skipped private supertypes][ExpandedSupertypes.privateSupertypes]
 */
private fun ClassBinarySignature.expandPrivateSupertypes(classResolver: ClassResolver): ExpandedSupertypes {
  val stack = ArrayDeque<String>()
  stack.addAll(supertypes)

  val visited = HashSet<String>()
  val supertypeNames = ArrayList<String>(supertypes.size)
  val privateSupertypes = ArrayList<ClassBinarySignature>(1)

  while (stack.isNotEmpty()) {
    val className = stack.removeLast()
    if (!visited.add(className)) {
      continue
    }
    val supertype = classResolver(className)
    if (supertype == null) {
      // library type
      supertypeNames.add(className)
    }
    else if (supertype.isEffectivelyPublic) {
      // public supertype, included in the dump separately
      supertypeNames.add(className)
    }
    else {
      privateSupertypes.add(supertype)
      stack.addAll(supertype.supertypes)
    }
  }
  return ExpandedSupertypes(supertypeNames.sorted(), privateSupertypes)
}

private fun MemberBinarySignature.isConstructorAccessor(): Boolean {
  if (!access.isSynthetic || name != "<init>") {
    return false
  }
  val argumentTypes = Type.getType(desc).argumentTypes
  if (argumentTypes.lastOrNull()?.className != "kotlin.jvm.internal.DefaultConstructorMarker") {
    return false
  }
  if (argumentTypes.size > 2) {
    // might be a constructor with default parameters
    // https://github.com/Kotlin/binary-compatibility-validator/issues/73
    // https://youtrack.jetbrains.com/issue/KT-51073
    return argumentTypes[argumentTypes.size - 2].className != "int"
  }
  else {
    // definitely not a constructor with default parameters
    return true
  }
}
