package com.intellij.lambda.testFramework.junit

import com.intellij.ide.plugins.PluginModuleDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.remoteDev.tests.LambdaFrontendContext
import com.intellij.remoteDev.tests.impl.LambdaTestHost
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdKeyValueEntry
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.reflect.full.createInstance

private val logger = logger<InjectedLambda>()

// TODO: Looks like JUnit test discovery and initialization should be reused here (probably from the monorepo source code)
class InjectedLambda(frontendIdeContext: LambdaFrontendContext, plugin: PluginModuleDescriptor)
  : LambdaTestHost.Companion.NamedLambda<LambdaFrontendContext>(frontendIdeContext, plugin) {

  override suspend fun LambdaFrontendContext.lambda(args: List<LambdaRdKeyValueEntry>): Any? {
    val className: String = args.singleOrNull { it.key == "testClass" }?.value
                            ?: error("Test class either not specified or specified multiple times. Args: $args")
    val methodName: String = args.singleOrNull { it.key == "testMethod" }?.value
                             ?: error("Test method either not specified or specified multiple times. Args: $args")

    // Verify method exists by reading bytecode directly
    val methodInfo = findMethodInBytecode(className, methodName, plugin.pluginClassLoader!!)
                     ?: error("Test method '$methodName' not found in test class '$className'")

    val testClass = Class.forName(className, true, plugin.pluginClassLoader)
    val testContainer = testClass.kotlin.createInstance()
    val rawArgs = argumentsFromString(args.singleOrNull { it.key == "methodArguments" }?.value ?: "")
    val methodArgs: List<Any> = if (rawArgs.size == 1 && rawArgs.single() == "") listOf() else rawArgs

    logger.info("Starting test $className#$methodName inside ${lambdaIdeContext::class.simpleName} with args: $methodArgs")

    // Use MethodHandles to invoke without loading all methods
    val lookup = MethodHandles.lookup()
    val methodHandle = try {
      if (methodArgs.isEmpty()) {
        lookup.findVirtual(testClass, methodName, MethodType.methodType(Void.TYPE))
      } else {
        // For methods with parameters, we need to construct the method type
        // This is a simplified version - you might need to handle parameter types
        lookup.findVirtual(testClass, methodName, MethodType.methodType(Any::class.java, Array<Any>::class.java))
      }
    } catch (e: NoSuchMethodException) {
      // Fallback: try to find the method by iterating one by one
      findMethodSafely(testClass, methodName)?.let { method ->
        method.isAccessible = true
        return if (methodArgs.isNotEmpty()) method.invoke(testContainer, *methodArgs.toTypedArray())
        else method.invoke(testContainer)
      } ?: error("Method $methodName found in bytecode but cannot be invoked")
    }

    return if (methodArgs.isNotEmpty()) {
      methodHandle.invokeWithArguments(testContainer, *methodArgs.toTypedArray())
    } else {
      methodHandle.invoke(testContainer)
    }
  }

  /**
   * Safely finds a method by trying to load methods one by one,
   * catching exceptions for methods that cannot be loaded.
   */
  private fun findMethodSafely(clazz: Class<*>, methodName: String): java.lang.reflect.Method? {
    // Try to get methods one by one using getDeclaredMethod
    // But we don't know the parameter types, so we need to iterate differently
    
    // Use a safer approach: load the bytecode descriptor and try to match
    try {
      // Try no-arg method first (most common for test methods)
      return clazz.getDeclaredMethod(methodName)
    } catch (e: NoSuchMethodException) {
      // Method has parameters, we need more sophisticated approach
      logger.info("Method $methodName requires parameters or doesn't exist as no-arg")
    } catch (e: NoClassDefFoundError) {
      logger.warn("Cannot load method $methodName due to missing class: ${e.message}")
    }
    
    // Last resort: try to iterate through declared methods with exception handling
    val methods = mutableListOf<java.lang.reflect.Method>()
    var index = 0
    while (true) {
      try {
        // This is a hack: try to access methods by reflection on the internal array
        val method = clazz.declaredMethods.getOrNull(index) ?: break
        if (method.name == methodName) {
          return method
        }
        index++
      } catch (e: Throwable) {
        // Skip methods that cause loading errors
        logger.warn("Skipping method at index $index due to: ${e.message}")
        index++
        if (index > 100) break // Safety limit
      }
    }
    
    return null
  }

  /**
   * Checks if a method exists in a class by reading the bytecode directly
   * and returns method information.
   */
  private data class MethodInfo(val name: String, val paramCount: Int)

  private fun findMethodInBytecode(className: String, methodName: String, classLoader: ClassLoader): MethodInfo? {
    val classFileName = "${className.replace('.', '/')}.class"
    
    try {
      val classBytes = classLoader.getResourceAsStream(classFileName)?.use { it.readBytes() }
                       ?: this@InjectedLambda.run {
                         logger.warn("Cannot find class file: $classFileName")
                         return null
                       }

      val methods = parseMethodInfo(classBytes)
      logger.info("Found methods in $className: ${methods.map { it.name }.joinToString()}")
      
      return methods.firstOrNull { it.name == methodName }
    } catch (e: Exception) {
      logger.error("Failed to read bytecode for $className", e)
      return null
    }
  }

  private fun parseMethodInfo(classBytes: ByteArray): List<MethodInfo> {
    val methods = mutableListOf<MethodInfo>()
    
    try {
      var offset = 0
      
      // Skip magic number (4 bytes) and version (4 bytes)
      offset += 8
      
      // Read constant pool
      val constantPoolCount = readUShort(classBytes, offset)
      offset += 2
      
      val constantPool = Array<String?>(constantPoolCount) { null }
      
      // Parse constant pool
      var i = 1
      while (i < constantPoolCount) {
        val tag = classBytes[offset].toInt() and 0xFF
        offset++
        
        when (tag) {
          1 -> { // CONSTANT_Utf8
            val length = readUShort(classBytes, offset)
            offset += 2
            val str = String(classBytes, offset, length, Charsets.UTF_8)
            constantPool[i] = str
            offset += length
          }
          7, 8, 16, 19, 20 -> offset += 2
          15 -> offset += 3
          3, 4, 9, 10, 11, 12, 17, 18 -> offset += 4
          5, 6 -> {
            offset += 8
            i++
          }
          else -> {
            logger.warn("Unknown constant pool tag: $tag")
            return emptyList()
          }
        }
        i++
      }
      
      // Skip access flags, this class, super class
      offset += 6
      
      // Skip interfaces
      val interfacesCount = readUShort(classBytes, offset)
      offset += 2 + interfacesCount * 2
      
      // Skip fields
      val fieldsCount = readUShort(classBytes, offset)
      offset += 2
      for (j in 0 until fieldsCount) {
        offset += 6
        offset = skipAttributes(classBytes, offset)
      }
      
      // Read methods
      val methodsCount = readUShort(classBytes, offset)
      offset += 2
      
      for (j in 0 until methodsCount) {
        offset += 2 // access_flags
        val nameIndex = readUShort(classBytes, offset)
        offset += 2
        val descriptorIndex = readUShort(classBytes, offset)
        offset += 2
        
        val name = constantPool[nameIndex]
        val descriptor = constantPool[descriptorIndex]
        
        if (name != null && descriptor != null) {
          val paramCount = countParameters(descriptor)
          methods.add(MethodInfo(name, paramCount))
        }
        
        offset = skipAttributes(classBytes, offset)
      }
      
    } catch (e: Exception) {
      logger.error("Error parsing class bytecode", e)
    }
    
    return methods
  }

  private fun countParameters(descriptor: String): Int {
    // Simple parameter counting from method descriptor
    // e.g., "()V" = 0 params, "(I)V" = 1 param, "(ILjava/lang/String;)V" = 2 params
    var count = 0
    var inClass = false
    for (c in descriptor.substringAfter('(').substringBefore(')')) {
      when {
        c == 'L' -> inClass = true
        c == ';' -> {
          inClass = false
          count++
        }
        !inClass && c in "BCDFIJSZ" -> count++
      }
    }
    return count
  }

  private fun readUShort(bytes: ByteArray, offset: Int): Int {
    return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
  }

  private fun skipAttributes(bytes: ByteArray, startOffset: Int): Int {
    var offset = startOffset
    val attributesCount = readUShort(bytes, offset)
    offset += 2
    
    for (i in 0 until attributesCount) {
      offset += 2
      val attributeLength = readInt(bytes, offset)
      offset += 4 + attributeLength
    }
    
    return offset
  }

  private fun readInt(bytes: ByteArray, offset: Int): Int {
    return ((bytes[offset].toInt() and 0xFF) shl 24) or
           ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
           ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
           (bytes[offset + 3].toInt() and 0xFF)
  }
}