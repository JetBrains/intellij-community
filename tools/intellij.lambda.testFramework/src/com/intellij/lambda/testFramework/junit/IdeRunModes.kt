package com.intellij.lambda.testFramework.junit

import com.intellij.ide.starter.ide.IdeRunMode
import com.intellij.util.containers.orNull
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.util.AnnotationUtils

/** The modes of the class [RunInMonolithAndSplitMode]. One class-template invocation is created per mode. */
internal fun getClassTemplateModesToRun(context: ExtensionContext): List<IdeRunMode> {
  return AnnotationUtils.findAnnotation(context.testClass, RunInMonolithAndSplitMode::class.java).orNull()?.mode?.toList()
         ?: emptyList()
}

/**
 * Returns the mode of the current Lambda IDE invocation.
 *
 * Use this when the caller must address exactly one reusable IDE session. It fails outside a class-template invocation.
 */
internal fun getCurrentRunMode(context: ExtensionContext): IdeRunMode {
  return getCurrentRunModeOrNull(context)
         ?: error(
           "Cannot determine Lambda IDE run mode for ${context.displayName}. " +
           "Use it inside a concrete @RunInMonolithAndSplitMode invocation."
         )
}

/** The mode that the current class-template invocation prepared, or null outside an invocation. */
internal fun getCurrentRunModeOrNull(context: ExtensionContext): IdeRunMode? {
  return context.getStore(RUN_MODE_NAMESPACE).get(RUN_MODE_KEY, IdeRunMode::class.java)
}

internal fun putInvocationRunMode(context: ExtensionContext, mode: IdeRunMode) {
  context.getStore(RUN_MODE_NAMESPACE).put(RUN_MODE_KEY, mode)
}

private val RUN_MODE_NAMESPACE = ExtensionContext.Namespace.create(RunInMonolithAndSplitMode::class.java)
private const val RUN_MODE_KEY = "lambdaRunMode"
