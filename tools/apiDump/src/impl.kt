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
    discoverClasses(signatures)

    handleAnnotationsAndVisibility(signatures)

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

  private fun resolveClass(className: String?): ClassBinarySignature? {
    return classes[className]
  }

  private fun discoverPackages(packages: Map<String, ApiAnnotations>) {
    for ((packageName, packageAnnotations) in packages) {
      check(this.packages[packageName] == null)
      this.packages[packageName] = packageAnnotations
    }
  }

  private fun discoverClasses(classSignatures: List<ClassBinarySignature>) {
    for (classSignature in classSignatures) {
      check(classes[classSignature.name] == null)
      classes[classSignature.name] = classSignature
    }
  }

  private val privateApi = HashSet<String>()
  private val experimentalApi = HashSet<String>()

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

  private fun handleAnnotationsAndVisibility(classSignatures: List<ClassBinarySignature>) {
    for (signature in classSignatures) {
      val className = signature.name
      val outerClassName = signature.outerName
      val packageAnnotations = packageAnnotations(className.packageName())
      val isPrivate = !signature.isEffectivelyPublic
                      || packageAnnotations.isInternal
                      || signature.annotations.isInternal()
                      || outerClassName in privateApi
                      || signature.access.isProtected && resolveClass(outerClassName)?.access?.isFinal == true
      if (isPrivate) {
        privateApi.add(className)
        continue
      }
      val isExperimental = packageAnnotations.isExperimental
                           || outerClassName in experimentalApi
                           || signature.annotations.isExperimental()
      if (isExperimental) {
        experimentalApi.add(className)
      }
    }
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
        flags = ApiFlags(signature.access.access, className in experimentalApi),
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

private data class ApiAnnotations(val isInternal: Boolean, val isExperimental: Boolean)

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

private fun List<AnnotationNode>?.isInternal(): Boolean {
  return hasAnnotation("Lorg/jetbrains/annotations/ApiStatus\$Internal;")
}

private fun List<AnnotationNode>?.isExperimental(): Boolean {
  return hasAnnotation("Lorg/jetbrains/annotations/ApiStatus\$Experimental;")
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
