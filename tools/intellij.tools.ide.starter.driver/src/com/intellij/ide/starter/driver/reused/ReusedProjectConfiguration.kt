package com.intellij.ide.starter.driver.reused

import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Resolves the [com.intellij.ide.starter.testContextCache.CachedProjectConfiguration] declared by an [ReusedRun] test class.
 *
 * Project properties are collected from the test class hierarchy, parent first. The closest
 * declaration to the concrete test class wins, so subclasses can replace the inherited project recipe.
 */
fun findReusedProjectConfiguration(testClass: Class<*>): CachedProjectConfiguration {
  val projectField = annotatedCompanionFields(testClass).lastOrNull()
                     ?: error(
                       "${testClass.name} must declare a companion val annotated with @${ReusedProject::class.simpleName}"
                     )
  return projectField.cachedProjectConfiguration()
}

/**
 * Marks the companion val that holds the [CachedProjectConfiguration] of a [ReusedRun] class.
 *
 * Several classes reuse one prepared context by pointing at one instance, so the val usually names a shared
 * configuration rather than building its own. A `by lazy` val works too, through `@delegate:ReusedProject`.
 */
@Suppress("unused")
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ReusedProject

/** The annotated field of each class of the hierarchy, parent first, so the closest declaration is last. */
private fun annotatedCompanionFields(testClass: Class<*>): List<CompanionField> {
  return testClass.selfAndSuperclasses().toList().asReversed().mapNotNull { currentClass ->
    val companion = currentClass.companionInstance() ?: return@mapNotNull null
    // A companion val has its backing field on the outer class, and a `by lazy` one on the companion.
    val annotated = (currentClass.declaredFields + companion.javaClass.declaredFields)
      .filter { field -> field.isAnnotationPresent(ReusedProject::class.java) }
      .map { field -> CompanionField(currentClass, field, companion) }
    check(annotated.size <= 1) {
      "${currentClass.name} must declare at most one companion property annotated with @${ReusedProject::class.simpleName}"
    }
    annotated.singleOrNull()
  }
}

private fun Class<*>.companionInstance(): Any? {
  val companionField = declaredFields.firstOrNull { field -> field.name == "Companion" } ?: return null
  companionField.isAccessible = true
  return companionField.get(null)
}

private class CompanionField(private val ownerClass: Class<*>, val field: Field, companion: Any) {
  val receiver: Any? = if (Modifier.isStatic(field.modifiers)) null else companion

  fun displayName(): String = ownerClass.name + "." + field.name.removeSuffix(LAZY_DELEGATE_SUFFIX)
}

/** The configuration the field holds, whether it holds it directly or through a `lazy` delegate. */
private fun CompanionField.cachedProjectConfiguration(): CachedProjectConfiguration {
  field.isAccessible = true
  val value = field.get(receiver)
  return (value as? CachedProjectConfiguration ?: (value as? Lazy<*>)?.value as? CachedProjectConfiguration)
         ?: error(
           "${displayName()} annotated with @${ReusedProject::class.simpleName} must be a val (or lazy val) of ${CachedProjectConfiguration::class.simpleName}"
         )
}

private const val LAZY_DELEGATE_SUFFIX = "\$delegate"
