package com.intellij.mcpserver.impl.util

import com.intellij.mcpserver.McpToolCategory
import com.intellij.mcpserver.McpToolDescriptor
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.annotations.McpToolHintValue
import com.intellij.mcpserver.annotations.McpToolHints
import com.intellij.mcpserver.impl.ReflectionCallableMcpTool
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.Nls
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.kotlinFunction

/**
 * Converts all instance methods of [this] class marked as [com.intellij.mcpserver.annotations.McpTool] to a list of tools.
 *
 * See [asTool] for detailed description.
 *
 * @param json The Json instance to use for serialization.
 *
 * ```
 * interface MyToolsetInterface : ToolSet {
 *     @McpTool
 *     @McpDescription("My best tool")
 *     fun my_best_tool(arg1: String, arg2: Int)
 * }
 *
 * class MyToolset : MyToolsetInterface {
 *     @McpTool
 *     @McpDescription("My best tool overridden description")
 *     fun my_best_tool(arg1: String, arg2: Int) {
 *         // ...
 *     }
 *
 *     @McpTool
 *     @McpDescription("My best tool 2")
 *     fun my_best_tool_2(arg1: String, arg2: Int) {
 *          // ...
 *     }
 * }
 *
 * val myToolset = MyToolset()
 * val tools = myToolset.asTools()
 * ```
 */
fun McpToolset.asTools(json: Json = McpServerJson): List<ReflectionCallableMcpTool> {
  return this::class.asTools(json = json, thisRef = this)
}

/**
 * Converts all functions of [this] class marked as [com.intellij.mcpserver.annotations.McpTool] to a list of tools.
 *
 * @param json The Json instance to use for serialization.
 * @param thisRef an instance of [this] class to be used as the 'this' object for the callable in the case of instance methods.

 * @see [asTool]
 */
@RequiresBackgroundThread
fun <T : McpToolset> KClass<out T>.asTools(json: Json = McpServerJson, thisRef: T? = null): List<ReflectionCallableMcpTool> {
  val category = McpToolCategory(
    shortName = this.simpleName ?: "Unknown",
    fullyQualifiedName = this.qualifiedName ?: "Unknown",
    isExperimental = thisRef?.isExperimental() ?: false,
    alwaysIncluded = thisRef?.alwaysIncluded() ?: false,
    displayName = thisRef?.displayName(),
    displayDescription = thisRef?.displayDescription(),
  )
  val presentableDescriptionProvider: ((String) -> @Nls String?)? = thisRef?.let { ts -> { toolName -> ts.displayDescription(toolName) } }
  return runBlockingMaybeCancellable {
    this@asTools.functions.map {
      async(Dispatchers.Default) {
        if (it.getPreferredToolAnnotation() == null) return@async null
        it.asTool(json = json,
                  thisRef = thisRef,
                  category = category,
                  fullyQualifiedName = this@asTools.qualifiedName + "." + it.name,
                  presentableDescriptionProvider = presentableDescriptionProvider,
                  additionalImplicitParameters = arrayOf(projectPathParameter))
      }
    }.awaitAll().filterNotNull()
  }.apply {
    require(isNotEmpty()) { "No tools found in ${this@asTools}" }
  }
}


/**
 * Converts a KFunction into a code-engine tool that works by reflection.
 *
 *
 * The function can be annotated with [com.intellij.mcpserver.annotations.McpTool] annotation where the name of the tool can be overridden.
 * If the custom name is not provided, the name of the function is used.
 *
 * The function can be annotated with [McpDescription] annotation to provide a description of the tool.
 * If not provided, the name of the function is used as a description.
 *
 * The callable parameters can be annotated with [McpDescription] annotation to provide a description of the parameter.
 * If not provided, the name of the parameter is used.
 *
 * If the function is a method that overrides or implements another method from a base class or an interface,
 * [com.intellij.mcpserver.annotations.McpTool] annotation can be extracted from one of the base methods in the case when it's missing on this method.
 * In this case [McpDescription]` annotation will be also extracted from the base method where [com.intellij.mcpserver.annotations.McpTool] annotation was found.
 *
 * Both suspend and non-suspend functions are supported
 *
 * Default parameters are supported (calling site can omit them in the argument Json)
 *
 * @param json The Json instance to use for serialization.
 * @param thisRef The object instance to use as the 'this' object for the callable.
 * @param name The name of the tool. If not provided, the name will be obtained from [com.intellij.mcpserver.annotations.McpTool.name] property.
 * In the case of the missing attribute or empty name the name of the function is used.
 * @param description The description of the tool. If not provided, the description will be obtained from [McpDescription.description] property.
 * In the case of the missing attribute or empty description the name of the function is used as a description.
 *
 *
 * # Note #
 * If you get the callable as a reference to an instance method like `myTools::my_best_tool`
 * you don't need to pass [thisRef] argument, but if the callable is a reference to an instance method obtained via class
 * you have to provide the proper value (`MyTools::my_best_tool`])
 *
 *
 * ```
 * class MyTools {
 *     @McpTool
 *     fun my_best_tool(arg1: String, arg2: Int) {
 *         // ...
 *     }
 * }
 *
 * val myTools = MyTools()
 * val tool = myTools::my_best_tool.asTool(json = Json)
 * // or
 *
 * val tool = MyTools::my_best_tool.asTool(json = Json, thisRef = myTools)
 * ```
 */
fun KFunction<*>.asTool(
  json: Json = McpServerJson,
  thisRef: Any? = null,
  name: String? = null,
  description: String? = null,
  category: McpToolCategory? = null,
  fullyQualifiedName: String? = null,
  presentableDescriptionProvider: ((toolName: String) -> @Nls String?)? = null,
  vararg additionalImplicitParameters: KParameter,
): ReflectionCallableMcpTool {
  val toolDescriptor =
    this.asToolDescriptor(name = name, description = description, category, fullyQualifiedName, presentableDescriptionProvider, *additionalImplicitParameters)
  if (instanceParameter != null && thisRef == null) error("Instance parameter is not null, but no 'this' object is provided")
  val callableBridge = CallableBridge(callable = this, thisRef = thisRef, json = json)
  return ReflectionCallableMcpTool(descriptor = toolDescriptor, callableBridge = callableBridge)
}


private val unknownCategory = McpToolCategory(shortName = "Unknown", fullyQualifiedName = "Unknown")

fun KFunction<*>.asToolDescriptor(
  name: String? = null,
  description: String? = null,
  category: McpToolCategory? = null,
  fullyQualifiedName: String? = null,
  presentableDescriptionProvider: ((toolName: String) -> @Nls String?)? = null,
  vararg additionalImplicitParameters: KParameter,
): McpToolDescriptor {
  val preferredToolAnnotation = this.getPreferredToolAnnotation()
  val toolName = name ?: preferredToolAnnotation?.name?.ifBlank { this.name } ?: this.name
  val toolTitle = preferredToolAnnotation?.title?.ifEmpty { null }
  val toolDescription = description ?: this.getPreferredToolDescriptionAnnotation()?.description?.trimMargin() ?: this.name
  val toolDisplayDescription = presentableDescriptionProvider?.invoke(toolName)
  val toolAnnotations = resolveToolAnnotations(this)

  val parametersSchema = parametersSchema(this, *additionalImplicitParameters)
  val returnTypeSchema = returnTypeSchema(this)
  return McpToolDescriptor(
    name = toolName,
    title = toolTitle,
    description = toolDescription,
    displayDescription = toolDisplayDescription,
    category = category ?: unknownCategory,
    fullyQualifiedName = fullyQualifiedName ?: toolName,
    inputSchema = parametersSchema,
    outputSchema = returnTypeSchema,
    annotations = toolAnnotations,
  )
}

private fun KFunction<*>.getPreferredToolAnnotation(): McpTool? {
  return getToolMethodAndAnnotation(this)?.second
}

private fun KFunction<*>.getPreferredToolDescriptionAnnotation(): McpDescription? {
  return getPreferredToolDescriptionAndMethod(this)?.second
}

private fun resolveToolAnnotations(function: KFunction<*>): ToolAnnotations? {
  val readOnlyHint = resolveToolHint(function, McpToolHints::readOnlyHint)
  val destructiveHint = resolveToolHint(function, McpToolHints::destructiveHint)
  val idempotentHint = resolveToolHint(function, McpToolHints::idempotentHint)
  val openWorldHint = resolveToolHint(function, McpToolHints::openWorldHint)
  if (readOnlyHint == null && destructiveHint == null && idempotentHint == null && openWorldHint == null) {
    return null
  }

  return ToolAnnotations(
    readOnlyHint = readOnlyHint,
    destructiveHint = destructiveHint,
    idempotentHint = idempotentHint,
    openWorldHint = openWorldHint,
  )
}

private fun resolveToolHint(function: KFunction<*>, selector: (McpToolHints) -> McpToolHintValue): Boolean? {
  for (method in toolAnnotationCandidates(function)) {
    val annotation = method.findAnnotation<McpToolHints>() ?: continue
    when (selector(annotation)) {
      McpToolHintValue.TRUE -> return true
      McpToolHintValue.FALSE -> return false
      McpToolHintValue.UNSPECIFIED -> Unit
    }
  }
  return null
}

private fun toolAnnotationCandidates(function: KFunction<*>): Sequence<KFunction<*>> {
  return sequence {
    yield(function)
    yieldAll(getImplementedMethods(function))
  }
}

private fun getToolMethodAndAnnotation(function: KFunction<*>): Pair<KFunction<*>, McpTool>? {
  // Annotation exactly on this function is preferred
  val thisAnnotation = function.findAnnotation<McpTool>()
  if (thisAnnotation != null) return function to thisAnnotation
  return getImplementedMethods(function).firstNotNullOfOrNull { m -> m.findAnnotation<McpTool>()?.let { m to it } }
}

private fun getPreferredToolDescriptionAndMethod(function: KFunction<*>): Pair<KFunction<*>, McpDescription>? {
  // Annotation exactly on this function is preferred
  val thisAnnotation = function.findAnnotation<McpDescription>()
  if (thisAnnotation != null) return function to thisAnnotation

  val (toolMethod, _) = getToolMethodAndAnnotation(function) ?: return null
  val mcpDescriptionAnnotation = toolMethod.findAnnotation<McpDescription>() ?: return null
  return toolMethod to mcpDescriptionAnnotation
}

/**
 * Retrieves a sequence of Kotlin functions that represent the methods implemented
 * by the current function, including methods from superclasses and interfaces in the
 * class hierarchy where applicable.
 *
 * The method identifies overridden or declared methods corresponding to the current
 * function and accounts for the Java method representation, name, parameter types,
 * and class traversal.
 *
 * @return A sequence of `KFunction` instances representing the implemented methods
 *         related to the current function.
 */
private fun getImplementedMethods(function: KFunction<*>): Sequence<KFunction<*>> {
  return sequence {
    val javaMethod = function.javaMethod ?: return@sequence
    val methodName = javaMethod.name
    val parameterTypes = javaMethod.parameterTypes
    val visited = HashSet<Class<*>>()
    val queue = ArrayDeque<Class<*>>()

    queue.add(javaMethod.declaringClass)

    while (queue.isNotEmpty()) {
      val currentClass = queue.removeFirstOrNull() ?: break
      if (!visited.add(currentClass)) continue

      // Check superclass
      currentClass.superclass?.let { superclass ->
        if (!visited.contains(superclass)) {
          queue.addLast(superclass)
        }
      }

      // Check interfaces
      for (iface in currentClass.interfaces) {
        if (!visited.contains(iface)) {
          queue.add(iface)
        }
      }

      try {
        val kotlinMethod = currentClass.getDeclaredMethod(methodName, *parameterTypes).kotlinFunction ?: continue
        if (kotlinMethod != function) yield(kotlinMethod)
      }
      catch (_: NoSuchMethodException) {
        // Method not found in this class/interface, continue traversal
      }
    }
  }
}
