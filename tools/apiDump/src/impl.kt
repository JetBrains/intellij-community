// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.apiDump

import kotlinx.metadata.jvm.JvmFieldSignature
import kotlinx.validation.api.*
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import java.nio.file.Path
import kotlin.collections.set
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.walk

class ApiIndex {

  /**
   * @return a list of classes (with members) in [root], which are considered API
   */
  fun api(root: Path): API {
    val classFilePaths: Sequence<Path> = classFilePaths(root)

    val packages: Map<String, ApiAnnotations> = classFilePaths.packages()
    discoverPackages(packages)

    val signatures: List<ClassBinarySignature> = classFilePaths
      .map { it.inputStream() }
      .loadApiFromJvmClasses()
      .map { it.removeSyntheticBridges() }
      .map { signature ->
        signature.handleAnnotationsAndVisibility().also {
          /**
           * Class has to be saved to the [classes] map in the same iteration
           * because the next [handleAnnotationsAndVisibility] call relies on it
           * to resolve the outer class name.
           */
          val className = it.name
          check(classes[className] == null)
          classes[className] = it
        }
      }

    discoverPrivateClasses(signatures)

    return API(
      publicApi = publicApi(signatures),
      privateApi,
    )
  }

  private val packages: MutableMap<String, ApiAnnotations> = HashMap()
  private val classes: MutableMap<String, ClassBinarySignature> = HashMap()

  private fun packageAnnotations(packageName: String): ApiAnnotations {
    return packages[packageName]
           ?: unannotated
  }

  private fun resolveClass(className: String): ClassBinarySignature? {
    return classes[className]
  }

  private fun discoverPackages(packages: Map<String, ApiAnnotations>) {
    for ((packageName, packageAnnotations) in packages) {
      check(this.packages[packageName] == null)
      this.packages[packageName] = packageAnnotations
    }
  }

  private fun discoverPrivateClasses(classSignatures: List<ClassBinarySignature>) {
    for (classSignature in classSignatures) {
      val apiAnnotations = classSignature.annotations.apiAnnotations()
      if (apiAnnotations.isInternal || !classSignature.isEffectivelyPublic) {
        privateApi.add(classSignature.name)
      }
    }
  }

  private val privateApi = HashSet<String>()

  /**
   * - Pushes [isEffectivelyPublic] from class to inner class.
   * - Pushes [@Internal][org.jetbrains.annotations.ApiStatus.Internal] and [@Experimental][org.jetbrains.annotations.ApiStatus.Experimental]
   * from package to class and from class to inner classes.
   */
  private fun ClassBinarySignature.handleAnnotationsAndVisibility(): ClassBinarySignature {
    if (!isEffectivelyPublic) {
      return this
    }
    val outerName = outerName
    val outerAnnotations = if (outerName != null) {
      val outerClass = resolveClass(outerName) ?: error("Outer class $outerName is unknown")
      if (!outerClass.isEffectivelyPublic || access.isProtected && outerClass.access.isFinal) {
        return copy(isEffectivelyPublic = false)
      }
      outerClass.annotations.apiAnnotations()
    }
    else {
      packageAnnotations(name.packageName())
    }
    return annotate(outerAnnotations)
  }

  private fun companionAnnotations(
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
    val companionSignature = resolveClass(type.internalName)
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
  private fun ClassBinarySignature.removePrivateSupertypes(): ClassBinarySignature {
    val privateSupertypes = supertypes(::resolveClass)
      .drop(1) // skip [this] signature
      .filter { it.name in privateApi }
      .toList()
    if (privateSupertypes.isEmpty()) {
      return this
    }
    val inheritedStaticSignatures = privateSupertypes.flatMap { superType ->
      superType.memberSignatures.filter { member ->
        member.access.isStatic
      }
    }
    return this.copy(
      memberSignatures = memberSignatures + inheritedStaticSignatures,
      supertypes = supertypes - privateSupertypes.map { it.name }.toSet()
    )
  }

  private fun publicApi(classSignatures: List<ClassBinarySignature>): List<ApiClass> {
    val publicSignatures: List<ClassBinarySignature> = classSignatures
      .filter { it.name !in privateApi }
      .map { it.removePrivateSupertypes() }

    val result = ArrayList<ApiClass>()
    for (signature in publicSignatures) {
      val className = signature.name
      val members = signature.memberSignatures
        .sortedWith(MEMBER_SORT_ORDER)
        .mapNotNull { memberSignature ->
          val companionAnnotations = companionAnnotations(signature, memberSignature) ?: unannotated
          if (memberSignature.annotations.isInternal() || companionAnnotations.isInternal) {
            return@mapNotNull null
          }
          if (memberSignature.isConstructorAccessor()) {
            return@mapNotNull null
          }
          ApiMember(
            ApiRef(memberSignature.name, memberSignature.desc),
            ApiFlags(memberSignature.access.access, memberSignature.annotations.isExperimental() || companionAnnotations.isExperimental),
          )
        }
      result += ApiClass(
        className,
        flags = ApiFlags(signature.access.access, signature.annotations.isExperimental()),
        supers = signature.supertypes,
        members,
      )
    }
    return result
  }
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

private data class ApiAnnotations(val isInternal: Boolean, val isExperimental: Boolean) {

  operator fun plus(other: ApiAnnotations): ApiAnnotations {
    if (other == unannotated && this == unannotated) {
      return unannotated
    }
    return ApiAnnotations(
      isInternal || other.isInternal,
      isExperimental || other.isExperimental,
    )
  }

  /**
   * @return which annotations are missing in [other] but present in this in form of [ApiAnnotations]
   */
  fun missingIn(other: ApiAnnotations): ApiAnnotations {
    val internalMissing = isInternal && !other.isInternal
    val experimentalMissing = isExperimental && !other.isExperimental
    return ApiAnnotations(internalMissing, experimentalMissing)
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

private fun List<AnnotationNode>?.isInternal(): Boolean {
  return hasAnnotation(API_STATUS_INTERNAL_DESCRIPTOR)
}

private fun List<AnnotationNode>?.isExperimental(): Boolean {
  return hasAnnotation(API_STATUS_EXPERIMENTAL_DESCRIPTOR)
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

private fun ClassBinarySignature.annotate(outerApiAnnotations: ApiAnnotations): ClassBinarySignature {
  val classApiAnnotations = annotations.apiAnnotations()
  val missingAnnotations = outerApiAnnotations.missingIn(classApiAnnotations)
  if (missingAnnotations == unannotated) {
    return this
  }
  return copy(annotations = annotations.addMissing(missingAnnotations))
}

private fun List<AnnotationNode>.addMissing(missingAnnotations: ApiAnnotations): List<AnnotationNode> {
  check(missingAnnotations != unannotated)
  val (internalMissing, experimentalMissing) = missingAnnotations
  val result = ArrayList(this)
  if (internalMissing) {
    result.add(AnnotationNode(API_STATUS_INTERNAL_DESCRIPTOR))
  }
  if (experimentalMissing) {
    result.add(AnnotationNode(API_STATUS_EXPERIMENTAL_DESCRIPTOR))
  }
  return result
}

private fun ClassBinarySignature.supertypes(classResolver: ClassResolver): Sequence<ClassBinarySignature> = sequence {
  val stack = ArrayDeque<ClassBinarySignature>()
  stack.addLast(this@supertypes)
  val visited = HashSet<String>()
  while (stack.isNotEmpty()) {
    val signature = stack.removeLast()
    if (!visited.add(signature.name)) {
      continue
    }
    yield(signature)
    for (supertype in signature.supertypes) {
      classResolver(supertype)?.let {
        stack.addLast(it)
      }
    }
  }
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
