// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.jetbrains.python.reflection.getProperties
import com.jetbrains.python.reflection.preloadProperties
import com.jetbrains.python.testing.autoDetectTests.PyAutoDetectTestConfiguration
import java.util.concurrent.atomic.AtomicBoolean

private val preloaded = AtomicBoolean()

/**
 * Fills the [getProperties] cache of the Python test configurations in the background.
 *
 * A run configuration producer clones these configurations under a read action.
 * Without the cache, the first clone runs slow Kotlin reflection under that read action and freezes the IDE.
 * The cache is global, so the activity does the work once per application.
 */
internal class PyTestConfigurationPropertiesPreloader : ProjectActivity, DumbAware {
  override suspend fun execute(project: Project) {
    if (!preloaded.compareAndSet(false, true)) return
    val types = listOf(
      PyAutoDetectTestConfiguration::class.java,
      PyTestConfiguration::class.java,
      PyNoseTestConfiguration::class.java,
      PyTrialTestConfiguration::class.java,
      PyUnitTestConfiguration::class.java,
      ConfigurationTarget::class.java,
    )
    for (type in types) {
      checkCanceled()
      preloadProperties(type)
    }
  }
}
