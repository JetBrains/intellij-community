package com.intellij.lambda.testFramework.testApi.utils

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.remoteDev.tests.LambdaIdeContext
import org.assertj.core.api.Assertions.assertThat
import java.util.function.BiPredicate

/**
 * Unregisters an extension for [epName] with specified [orderId]
 * @return true if a matching extension is unregistered, otherwise false
 */
context(lambdaIdeContext: LambdaIdeContext)
fun ComponentManager.unregisterExtension(epName: ExtensionPointName<*>, orderId: String): Boolean {
  return extensionArea.getExtensionPoint(epName).unregisterExtensions(BiPredicate { t, u ->
    val id = u.orderId ?: return@BiPredicate true
    return@BiPredicate id != orderId
  }, false)
}

/**
 * The same as [unregisterExtension] but asserts that the extension was found and unregistered
 */
context(lambdaIdeContext: LambdaIdeContext)
fun ComponentManager.unregisterExtensionStrictly(epName: ExtensionPointName<*>, orderId: String, additionalMessage: String = "") {
  assertThat(unregisterExtension (epName, orderId))
    .withFailMessage("Extension with id=$orderId is not found. $additionalMessage")
    .isTrue()
}