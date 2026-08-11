// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.rpc

import com.intellij.ui.webview.api.WebViewCallable
import com.intellij.ui.webview.api.WebViewMessageRegistration
import com.intellij.ui.webview.api.WebViewNotification
import com.intellij.ui.webview.api.validateWebViewApiNamespace

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.jvmErasure

internal inline fun <reified T : Any> WebViewMessageBusImpl.bindApi(
  implementation: T,
  namespace: String,
): WebViewMessageRegistration = bindApi(T::class, implementation, namespace)

internal fun <T : Any> WebViewMessageBusImpl.bindApi(
  api: KClass<T>,
  implementation: T,
  namespace: String,
): WebViewMessageRegistration {
  return bindApiImplementation(api, implementation, namespace)
}

internal fun <T : Any> WebViewMessageBusImpl.bindApiImplementation(
  api: KClass<T>,
  implementation: T,
  namespace: String,
): WebViewMessageRegistration {
  val validatedNamespace = validateWebViewApiNamespace(namespace)
  check(api.java.isInterface) { "WebView API binding type must be an interface: ${api.qualifiedName ?: api.simpleName}" }
  check(api.java.isInstance(implementation)) { "WebView API implementation must implement ${api.qualifiedName ?: api.simpleName}" }

  val functions = api.declaredMemberFunctions.filter { it.visibility == KVisibility.PUBLIC }
  val duplicateFunction = functions.groupBy { it.name }.entries.firstOrNull { it.value.size > 1 }
  check(duplicateFunction == null) { "WebView API binding does not support overloaded functions: $validatedNamespace/${duplicateFunction!!.key}" }

  val bindings = functions.map { function -> createImplementableBinding(validatedNamespace, function) }
  val registrations = ArrayList<WebViewMessageRegistration>(bindings.size + 1)
  try {
    registrations += reserveApiMethods(bindings.map { binding ->
      WebViewApiMethodRegistration(binding.method, WebViewApiMethodSource(api.apiName(), binding.function.name))
    })
    for (binding in bindings) {
      when (binding.kind) {
        WebViewApiMethodKind.CALL -> {
          registrations += registerApiCallHandler(
            method = binding.method,
            paramsSerializer = binding.paramsSerializer,
            resultSerializer = binding.resultSerializer,
          ) { params, _ ->
            binding.invokeCall(implementation, params)
          }
        }
        WebViewApiMethodKind.NOTIFICATION -> {
          registrations += registerNotificationHandler(ReflectedNotification(binding.method, binding.paramsSerializer)) { params, _ ->
            binding.invokeNotification(implementation, params)
          }
          registrations += registerApiCallHandler(
            method = binding.method,
            paramsSerializer = binding.paramsSerializer,
            resultSerializer = unitSerializer(),
          ) { params, _ ->
            binding.invokeNotification(implementation, params)
          }
        }
      }
    }
  }
  catch (t: Throwable) {
    registrations.forEach { registration ->
      runCatching { registration.close() }
    }
    throw t
  }
  return WebViewCompositeMessageRegistration(registrations)
}

internal fun <T : WebViewCallable> WebViewMessageBusImpl.createCallableProxy(api: KClass<T>, namespace: String): T {
  val validatedNamespace = validateWebViewApiNamespace(namespace)
  check(api.java.isInterface) { "WebView callable API type must be an interface: ${api.qualifiedName ?: api.simpleName}" }

  val functions = api.declaredMemberFunctions.filter { it.visibility == KVisibility.PUBLIC }
  val duplicateFunction = functions.groupBy { it.name }.entries.firstOrNull { it.value.size > 1 }
  check(duplicateFunction == null) { "WebView callable API does not support overloaded functions: $validatedNamespace/${duplicateFunction!!.key}" }

  val bindings = functions.map { function -> createCallableBinding(validatedNamespace, function) }
  val bindingsByMethod = bindings.associateBy { it.javaMethod }
  val classLoader = api.java.classLoader ?: WebViewMessageBusImpl::class.java.classLoader
  @Suppress("UNCHECKED_CAST")
  return Proxy.newProxyInstance(classLoader, arrayOf(api.java), InvocationHandler { proxy, method, args ->
    handleObjectMethod(proxy, method, args, api, validatedNamespace) ?: run {
      val binding = bindingsByMethod[method]
        ?: error("WebView callable API method is not registered: $validatedNamespace/${method.name}")
      notifyNow(binding.method, binding.paramsSerializer, binding.params(args))
    }
  }) as T
}

private enum class WebViewApiMethodKind {
  CALL,
  NOTIFICATION,
}

private data class WebViewApiBinding(
  val method: String,
  val paramsSerializer: KSerializer<Any>,
  val resultSerializer: KSerializer<Any>,
  val function: KFunction<*>,
  val valueParameter: KParameter?,
  val kind: WebViewApiMethodKind,
) {
  suspend fun invokeCall(implementation: Any, params: Any): Any {
    return try {
      if (valueParameter == null) {
        function.callSuspend(implementation) ?: Unit
      }
      else {
        function.callSuspend(implementation, params) ?: Unit
      }
    }
    catch (e: CancellationException) {
      throw e
    }
  }

  fun invokeNotification(implementation: Any, params: Any) {
    try {
      if (valueParameter == null) {
        function.call(implementation)
      }
      else {
        function.call(implementation, params)
      }
    }
    catch (e: CancellationException) {
      throw e
    }
  }
}

private data class WebViewCallableBinding(
  val method: String,
  val paramsSerializer: KSerializer<Any>,
  val valueParameter: KParameter?,
  val javaMethod: Method,
) {
  fun params(args: Array<Any?>?): Any {
    return if (valueParameter == null) {
      Unit
    }
    else {
      check(args != null && args.size == 1) { "WebView callable invocation must have exactly one argument: $method" }
      checkNotNull(args[0]) { "WebView callable parameter must be non-null: $method" }
    }
  }
}

private data class ReflectedNotification(
  override val method: String,
  override val paramsSerializer: KSerializer<Any>,
) : WebViewNotification<Any>

private fun createImplementableBinding(namespace: String, function: KFunction<*>): WebViewApiBinding {
  validateCommonFunctionShape(namespace, function)

  function.isAccessible = true
  val method = "$namespace/${function.name}"
  val parameter = valueParameters(function, method).singleOrNull()
  val paramsSerializer = parameter?.type?.let { serializerForApiType(it, "params", method) } ?: unitSerializer()
  val kind = if (function.isSuspend) WebViewApiMethodKind.CALL else WebViewApiMethodKind.NOTIFICATION
  val resultSerializer = when (kind) {
    WebViewApiMethodKind.CALL -> serializerForApiType(function.returnType, "result", method)
    WebViewApiMethodKind.NOTIFICATION -> {
      check(function.returnType.isUnit()) { "WebView API notification function must return Unit: $method" }
      unitSerializer()
    }
  }
  return WebViewApiBinding(
    method = method,
    paramsSerializer = paramsSerializer,
    resultSerializer = resultSerializer,
    function = function,
    valueParameter = parameter,
    kind = kind,
  )
}

private fun createCallableBinding(namespace: String, function: KFunction<*>): WebViewCallableBinding {
  val method = "$namespace/${function.name}"
  validateCommonFunctionShape(namespace, function)
  check(!function.isSuspend) { "WebView callable function must be a non-suspend notification: $method" }
  check(function.returnType.isUnit()) { "WebView callable function must return Unit: $method" }

  val parameter = valueParameters(function, method).singleOrNull()
  val paramsSerializer = parameter?.type?.let { serializerForApiType(it, "params", method) } ?: unitSerializer()
  val javaMethod = checkNotNull(function.javaMethod) { "WebView callable function must have a JVM method: $method" }
  return WebViewCallableBinding(method, paramsSerializer, parameter, javaMethod)
}

private fun validateCommonFunctionShape(namespace: String, function: KFunction<*>) {
  val method = "$namespace/${function.name}"
  check(function.parameters.none { it.kind == KParameter.Kind.EXTENSION_RECEIVER }) {
    "WebView API function must not be an extension function: $method"
  }
  valueParameters(function, method)
}

private fun valueParameters(function: KFunction<*>, method: String): List<KParameter> {
  val valueParameters = function.parameters.filter { it.kind == KParameter.Kind.VALUE }
  check(valueParameters.size <= 1) { "WebView API function must have zero or one parameter: $method" }
  return valueParameters
}

private fun handleObjectMethod(proxy: Any, method: Method, args: Array<Any?>?, api: KClass<*>, namespace: String): Any? {
  if (method.declaringClass != Any::class.java) return null
  return when (method.name) {
    "toString" -> "WebView callable proxy ${api.qualifiedName ?: api.simpleName}($namespace)"
    "hashCode" -> System.identityHashCode(proxy)
    "equals" -> proxy === args?.singleOrNull()
    else -> null
  }
}

@OptIn(ExperimentalSerializationApi::class)
private fun serializerForApiType(type: KType, role: String, method: String): KSerializer<Any> {
  check(!type.isMarkedNullable) { "WebView API $role type must be non-nullable: $method" }
  if (type.isUnit()) {
    return unitSerializer()
  }
  @Suppress("UNCHECKED_CAST")
  return serializer(type) as KSerializer<Any>
}

private fun KType.isUnit(): Boolean = jvmErasure == Unit::class

private fun KClass<*>.apiName(): String = qualifiedName ?: java.name

@Suppress("UNCHECKED_CAST")
private fun unitSerializer(): KSerializer<Any> = Unit.serializer() as KSerializer<Any>

private class WebViewCompositeMessageRegistration(
  private val registrations: List<WebViewMessageRegistration>,
) : WebViewMessageRegistration {
  override fun close() {
    registrations.forEach { registration -> registration.close() }
  }
}
