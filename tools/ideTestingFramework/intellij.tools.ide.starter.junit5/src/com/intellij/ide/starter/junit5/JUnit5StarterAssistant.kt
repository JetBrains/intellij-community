package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.bus.StarterListener
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.killOutdatedProcessesOnUnix
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.TestContainer
import com.intellij.ide.starter.runner.TestContainerImpl
import com.intellij.ide.starter.utils.logError
import com.intellij.ide.starter.utils.logOutput
import com.intellij.ide.starter.utils.withIndent
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.kodein.di.direct
import org.kodein.di.instance
import java.lang.reflect.Method
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

open class JUnit5StarterAssistant : BeforeEachCallback, AfterEachCallback {

  protected fun getProperty(testInstance: Any, propertyType: KClass<*>): KProperty1<out Any, *>? {
    val properties = testInstance::class.memberProperties

    try {
      val contextField = properties.single { property ->
        if (property.javaField == null) false
        else property.javaField!!.type.equals(propertyType.javaObjectType)
      }
      return contextField
    }
    catch (t: Throwable) {
      logError("Unable to get property of type ${propertyType.simpleName} in ${testInstance::class.qualifiedName}")
    }

    return null
  }

  open fun injectTestContainerProperty(testInstance: Any) {
    val containerProp = getProperty(testInstance, TestContainerImpl::class)
    if (containerProp != null) {
      val containerInstance = TestContainerImpl()

      try {
        containerProp.javaField!!.trySetAccessible()

        if (containerProp.javaField!!.get(testInstance) != null) {
          logOutput("Property `${containerProp.name}` already manually initialized in the code")
          return
        }

        containerProp.javaField!!.set(testInstance, containerInstance)
      }
      catch (e: Throwable) {
        logError("Unable to inject value for property `${containerProp.name}`")
      }
    }
  }

  protected fun injectTestInfoProperty(context: ExtensionContext) {
    val testInstance = context.testInstance.get()

    val testInfoProperty = getProperty(testInstance, TestInfo::class)
    if (testInfoProperty != null) {
      val testInfoInstance = object : TestInfo {
        override fun getDisplayName(): String = context.displayName
        override fun getTags(): MutableSet<String> = context.tags
        override fun getTestClass(): Optional<Class<*>> = context.testClass
        override fun getTestMethod(): Optional<Method> = context.testMethod
      }

      try {
        testInfoProperty.javaField!!.trySetAccessible()

        if (testInfoProperty.javaField!!.get(testInstance) != null) {
          logOutput("Property `${testInfoProperty.name}` already manually initialized in the code")
          return
        }

        testInfoProperty.javaField!!.set(testInstance, testInfoInstance)
      }
      catch (e: Throwable) {
        logError("Unable to inject value for property `${testInfoProperty.name}`")
      }
    }
  }

  override fun beforeEach(context: ExtensionContext) {
    if (context.testMethod.isPresent) {
      di.direct.instance<CurrentTestMethod>().set(context.testMethod.get())
    }
    else {
      logError("Couldn't acquire test method")
    }

    if (di.direct.instance<CIServer>().isBuildRunningOnCI) {
      logOutput(buildString {
        appendLine("Disk usage diagnostics before test ${context.displayName}")
        appendLine(di.direct.instance<GlobalPaths>().getDiskUsageDiagnostics().withIndent("  "))
      })
    }

    killOutdatedProcessesOnUnix()

    val testInstance = context.testInstance.get()

    injectTestContainerProperty(testInstance)
    injectTestInfoProperty(context)
  }

  protected inline fun <reified T : TestContainer<T>> closeResourcesOfTestContainer(context: ExtensionContext) {
    val testInstance = context.testInstance.get()
    val containerProp = getProperty(testInstance, T::class)

    if (containerProp != null) {
      try {
        (containerProp.javaField!!.apply { trySetAccessible() }.get(testInstance) as T).close()
      }
      catch (e: Throwable) {
        logError("Unable automatically to close resources of ${containerProp.name}")
      }
    }
  }

  override fun afterEach(context: ExtensionContext) {
    StarterListener.unsubscribe()

    closeResourcesOfTestContainer<TestContainerImpl>(context)
  }
}



