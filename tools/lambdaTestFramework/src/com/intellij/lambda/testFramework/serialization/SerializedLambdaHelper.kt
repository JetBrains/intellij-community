package com.intellij.remoteDev.tests.impl.utils.serialization

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.PathManager
import java.io.*
import java.util.*
import java.util.function.Consumer
import kotlin.io.inputStream
import kotlin.use

/**
 * Executes a given lambda (it must be serializable) inside
 * IntelliJ-based IDE process with a special classloader,
 * where all classes of all plugins are visible.
 *
 * Code is executed in a background thread, the thread is guaranteed
 * to be NOT an EDT/AWT dispatch thread. Use ApplicationManager.getApplication.invoke*
 * methods family and/or Read/Write locks to deal with entities inside IntelliJ.
 *
 * IntelliJ instance is disposed (via Application#exit) after the lambda is completed.
 * It's a responsibility of your test to make sure you are not leaking an alive
 * IDE instance in any possible scenario.
 *
 * You are fine to use any TBE plugin classes from that lambda
 *
 * https://plugins.jetbrains.com/docs/intellij/general-threading-rules.html
 */

data class SerializedLambda(
  val clazzName: String,
  val methodName: String,
  val serializedDataBase64: String,
  val classPath: Set<File>,
) {
  companion object {
    init {
      System.setProperty("sun.io.serialization.extendedDebugInfo", "true")
    }

    inline fun fromLambdaWithApplication(crossinline code: (Application) -> Unit): SerializedLambda {
      val obj = object : Consumer<Application>, Serializable {
        override fun accept(application: Application) {
          code(application)
        }
      }

      return wrapLambda(obj)
    }
  }
}


//this class is used from IntelliJ process
class SerializedLambdaLoader {
  fun save(obj: Any): String {
    return ByteArrayOutputStream().use {
      ObjectOutputStream(it).writeObject(obj)
      Base64.getEncoder().encodeToString(it.toByteArray())
    }
  }

  class ClassLoaderObjectInputStream(
    inputStream: InputStream,
    private val classLoader: ClassLoader,
  ) : ObjectInputStream(inputStream) {

    override fun resolveClass(desc: ObjectStreamClass): Class<*> {
      return Class.forName(desc.name, false, classLoader)
    }
  }

  @Suppress("UNCHECKED_CAST")
  fun load(stringToDecode: String, classLoader: ClassLoader = javaClass.classLoader): Consumer<Application> {
    val inputStream = Base64.getDecoder().decode(stringToDecode).inputStream()
    return ClassLoaderObjectInputStream(inputStream, classLoader)
      .readObject() as? Consumer<Application> ?: error("Failed to load Consumer<Application> from the lambda")
  }
}

private fun normalizeLambdaClassName(name: String): String {
  val slash = name.indexOf('/')
  return if (slash >= 0) name.substring(0, slash) else name
}

fun wrapLambda(obj: Consumer<Application>): SerializedLambda {
  val clazzPath = setOf(SerializedLambdaLoader::class.java, obj.javaClass, Application::class.java)
    .mapNotNull { PathManager.getJarPathForClass(it) }
    .map { File(it) }
    .toSet()

  val persistedLambda: String
  try {
    persistedLambda = SerializedLambdaLoader().save(obj)
    val reloadedLambda = SerializedLambdaLoader().load(persistedLambda)
    require(reloadedLambda.javaClass == obj.javaClass) {
      "The reloaded lambda should have the same type as the original one. " +
      "Reloaded Type is ${reloadedLambda.javaClass.name}, expected type is ${obj.javaClass.name}"
    }
  }
  catch (t: Throwable) {
    throw Error("Failed to save/load the lambda. Most likely, " +
                "the current lambda was more complex and so Kotlin compiler decided " +
                "to generate a more complicated constructor for a wrapper class. " +
                "Try to simplify the code, cleanup variables from the closure, copy parameters to the local scope, and try again. ${t.message}", t)
  }

  return SerializedLambda(
    clazzName = obj.javaClass.name,
    methodName = obj::accept.name,
    serializedDataBase64 = persistedLambda,
    classPath = clazzPath
  )
}

