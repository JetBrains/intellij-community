package com.intellij.mermaid.lang.preview

import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith

private class JcefTestModeExtension: RegistryKeyExtension("ide.browser.jcef.testMode.enabled", true)

private class JcefEnabledExtension: RegistryKeyExtension("ide.browser.jcef.enabled", true)

private class JcefHeadlessExtension: RegistryKeyExtension("ide.browser.jcef.headless.enabled", true)


@TestOnly
@Target(AnnotationTarget.CLASS)
@ExtendWith(
  JcefTestModeExtension::class,
  JcefEnabledExtension::class,
  JcefHeadlessExtension::class
)
internal annotation class WithJcef
