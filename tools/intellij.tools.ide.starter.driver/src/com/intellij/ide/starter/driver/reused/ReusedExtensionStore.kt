package com.intellij.ide.starter.driver.reused

import com.intellij.ide.starter.driver.reused.ReusedSessionStrategy.Companion.createReusedSessionStrategy
import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import org.jetbrains.annotations.ApiStatus
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.support.AnnotationSupport

/**
 * Chooses the JUnit store that owns reused IDE state.
 *
 * One IDE per test class, so the owner is always the context of that class and its store closes the session
 * with it.
 */
@ApiStatus.Internal
object ReusedExtensionStore {
  fun getClassStore(context: ExtensionContext): ExtensionContext.Store =
    reusedIdeClassContext(context).getStore(ExtensionContext.Namespace.create(ReusedExtension::class.java, context.requiredTestClass))

  private fun reusedIdeClassContext(context: ExtensionContext): ExtensionContext {
    val requiredTestClass = context.requiredTestClass
    var current = context
    while (true) {
      val parent = current.parent.orElse(null) ?: return current
      if (parent.testClass.orElse(null) != requiredTestClass) {
        return current
      }
      current = parent
    }
  }

  /**
   * The [ReusedSessionStrategy] of [context]'s test class, resolved once per class.
   *
   * Lives here because the strategy decides the session key, and so which store owns a session. Anything that
   * has to agree with the extension about that reads this instance, never a second one.
   */
  fun sessionStrategy(context: ExtensionContext): ReusedSessionStrategy =
    perClass(context, SESSION_STRATEGY_KEY, ReusedSessionStrategy::class.java) {
      createReusedSessionStrategy(reusedRun(context).sessionStrategy)
    }

  /** The [CachedProjectConfiguration] of [context]'s test class, resolved once per class. */
  fun projectConfiguration(context: ExtensionContext): CachedProjectConfiguration =
    perClass(context, PROJECT_CONFIGURATION_KEY, CachedProjectConfiguration::class.java) {
      findReusedProjectConfiguration(context.requiredTestClass)
    }

  private fun reusedRun(context: ExtensionContext): ReusedRun =
    AnnotationSupport.findAnnotation(context.requiredTestClass, ReusedRun::class.java).orElse(null)
    ?: error("${context.requiredTestClass.name} must be annotated with @${ReusedRun::class.simpleName}")

  private fun <T : Any> perClass(context: ExtensionContext, key: String, type: Class<T>, create: () -> T): T =
    getClassStore(context).computeIfAbsent(key, { create() }, type)

  private const val SESSION_STRATEGY_KEY = "reusedIdeSessionStrategy"
  private const val PROJECT_CONFIGURATION_KEY = "reusedIdeProjectConfiguration"
}
