package com.intellij.lambda.testFramework.starter

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.starter.product.idea.ultimate.IdeaUltimate

/**
 * How the lambda framework starts its IDE, and — through [key] — when two requests may share one.
 *
 * [IdeInstance] keeps one IDE alive across test classes and restarts it only when the requested
 * configuration changes, so what "changes" means is this class's whole job. It cannot mean "some field
 * differs": [configureTestContext] and [configureRunContext] are lambdas, and the callers are
 * `BeforeAllCallback`s that rebuild their config once per test class, handing over a fresh closure every
 * time. Comparing those makes every class look new and restarts the IDE for each one — which is what this
 * type did as a `data class`. [testCase] is no safer, because `ProjectInfoSpec` implementations carry
 * lambdas of their own, so two descriptions of one project need not be equal either.
 *
 * [key] is the identity instead, and it is the caller's promise rather than something checked here: two
 * configs carrying the same key must configure the IDE identically, because the second one is answered with
 * the IDE the first one started. A caller whose configuration varies folds what varies into the key — a
 * fingerprint over the inputs its lambdas read is the usual shape.
 */
class IdeStartConfig(
  val key: String,
  val testCase: TestCase<*> = (object : TestCaseTemplate(IdeInfo.IdeaUltimate) {}).withProject(NoProject),
  val configureTestContext: (IDETestContext.() -> Unit) = defaultTestContextConfig,
  val configureRunContext: (IDERunContext.() -> Unit) = defaultRunContextConfig,
) {
  init {
    require(key.isNotBlank()) { "IdeStartConfig key must not be blank: it is what decides whether an IDE is reused" }
  }

  override fun equals(other: Any?): Boolean = this === other || (other is IdeStartConfig && key == other.key)

  override fun hashCode(): Int = key.hashCode()

  override fun toString(): String = "IdeStartConfig(key=$key)"

  companion object {
    private val defaultTestContextConfig: (IDETestContext.() -> Unit) = {}
    private val defaultRunContextConfig: (IDERunContext.() -> Unit) = {}

    const val DEFAULT_KEY: String = "default"

    val default: IdeStartConfig = IdeStartConfig(key = DEFAULT_KEY)

    var current: IdeStartConfig = default
  }
}
