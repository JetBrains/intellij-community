package com.intellij.lambda.testFramework.junit

import com.intellij.util.containers.orNull
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.util.AnnotationUtils
import java.lang.reflect.AnnotatedElement

/** List of available IDE modes (monolith, split) */
enum class IdeRunMode {
  MONOLITH, SPLIT
}

fun getModesToRun(annotatedElement: AnnotatedElement?): List<IdeRunMode> {
  if (annotatedElement == null) return emptyList()
  val annotation = AnnotationUtils.findAnnotation(annotatedElement, RunInMonolithAndSplitMode::class.java).orNull()

  return annotation?.mode?.toList() ?: emptyList()
}

fun getModesToRun(context: ExtensionContext): List<IdeRunMode> {
  val annotation = AnnotationUtils.findAnnotation(context.testMethod, RunInMonolithAndSplitMode::class.java).orElse(
    AnnotationUtils.findAnnotation(context.testClass, RunInMonolithAndSplitMode::class.java).orNull()
  )

  if (annotation == null) throw IllegalStateException("The test is expected to have ${RunInMonolithAndSplitMode::javaClass.name} annotation")

  // Check if we're running under GroupByModeTestEngine with a mode filter
  val modeFilter = context.getConfigurationParameter("ide.run.mode.filter").orNull()

  // If mode filter is set, only return that mode
  return if (modeFilter != null) {
    listOf(IdeRunMode.valueOf(modeFilter))
  }
  else {
    annotation.mode.toList()
  }
}