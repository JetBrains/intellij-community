package com.intellij.lambda.testFramework.junit

import com.intellij.ide.plugins.PluginModuleDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.remoteDev.tests.LambdaFrontendContext
import com.intellij.remoteDev.tests.impl.LambdaTestHost
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdKeyValueEntry
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredMembers

private val logger = logger<InjectedLambda>()

class InjectedLambda(frontendIdeContext: LambdaFrontendContext, plugin: PluginModuleDescriptor)
  : LambdaTestHost.Companion.NamedLambda<LambdaFrontendContext>(frontendIdeContext, plugin) {

  // TODO: Looks like JUnit test discovery and initialization should be reused here (probably from the monorepo source code)
  override suspend fun LambdaFrontendContext.lambda(args: List<LambdaRdKeyValueEntry>): Any? {
    val className: String = args.singleOrNull { it.key == "testClass" }?.value
                            ?: error("Test class either not specified or specified multiple times. Args: $args")
    val methodName: String = args.singleOrNull { it.key == "testMethod" }?.value
                             ?: error("Test method either not specified or specified multiple times. Args: $args")

    val testClass = Class.forName(className, true, plugin.pluginClassLoader).kotlin

    val method = testClass.declaredMembers.singleOrNull { it.name == methodName }
                 ?: error("Test method '$methodName' not found in test class '$className'")


    val testContainer = testClass.createInstance()
    val rawArgs = argumentsFromString(args.singleOrNull { it.key == "methodArguments" }?.value ?: "")
    val args: List<Any> = if (rawArgs.size == 1 && rawArgs.single() == "") listOf() else rawArgs

    logger.info("Starting test $className#$methodName inside ${lambdaIdeContext::class.simpleName} with args: $args")

    return if (args.isNotEmpty()) method.call(testContainer, *args.toTypedArray())
    else method.call(testContainer)
  }
}