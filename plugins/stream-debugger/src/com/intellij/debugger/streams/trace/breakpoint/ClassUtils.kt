// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.compiler.server.BuildManager
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.ClassLoadingUtils
import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.compiler.ClassObject
import com.intellij.openapi.compiler.CompilationException
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.sun.jdi.Method
import com.sun.jdi.ReferenceType
import org.jetbrains.jps.model.java.JpsJavaSdkType
import java.io.File
import java.io.IOException

private val PRIMITIVE_TYPE_NAMES = listOf("boolean", "char", "short", "int", "long", "float", "double")

/**
 * @author Shumaf Lovpache
 */

/**
 * Almost completely copied from [com.intellij.debugger.ui.impl.watch.CompilingEvaluatorImpl.compile]
 */
@Throws(EvaluateException::class)
fun compileJavaCode(className: String, sourceCode: String, evaluationContext: EvaluationContextImpl): ClassObject? {
  val project = evaluationContext.project
  val process = evaluationContext.debugProcess
  val debuggeeVersion = JavaSdkVersion.fromVersionString(process.virtualMachineProxy.version())

  val options: MutableList<String> = ArrayList()
  options.add("-encoding")
  options.add("UTF-8")
  val platformClasspath: MutableList<File> = ArrayList()
  val classpath: MutableList<File> = ArrayList()
  val languageLevel = debuggeeVersion?.maxLanguageLevel
  val rootManager = ProjectRootManager.getInstance(project)
  for (s in rootManager.orderEntries().compileOnly().recursively().exportedOnly().withoutSdk().pathsList.pathList) {
    classpath.add(File(s))
  }
  for (s in rootManager.orderEntries().compileOnly().sdkOnly().pathsList.pathList) {
    platformClasspath.add(File(s))
  }
  if (languageLevel != null && languageLevel.isPreview) {
    options.add(JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY)
  }
  val runtime: Pair<Sdk, JavaSdkVersion> = BuildManager.getJavacRuntimeSdk(project)
  val buildRuntimeVersion = runtime.getSecond()
  // if compiler or debuggee version or both are unknown, let source and target be the compiler's defaults
  if (buildRuntimeVersion != null && debuggeeVersion != null) {
    val minVersion = if (debuggeeVersion < buildRuntimeVersion) debuggeeVersion else buildRuntimeVersion
    val sourceOption = JpsJavaSdkType.complianceOption(minVersion.maxLanguageLevel.toJavaVersion())
    options.add("-source")
    options.add(sourceOption)
    options.add("-target")
    options.add(sourceOption)
  }
  val compilerManager = CompilerManager.getInstance(project)
  var sourceFile: File? = null
  try {
    sourceFile = generateTempSourceFile(className, sourceCode, compilerManager.javacCompilerWorkingDir!!)
    val srcDir = sourceFile.parentFile
    val sourcePath = emptyList<File>()
    val sources = setOf<File?>(sourceFile)
    return compilerManager.compileJavaCode(options, platformClasspath, classpath, emptyList(), emptyList(), sourcePath,
                                           sources, srcDir).firstOrNull()
  }
  catch (e: CompilationException) {
    val res = StringBuilder("Compilation failed:\n")
    for (m in e.messages) {
      if (m.category === CompilerMessageCategory.ERROR) {
        res.append(m.text).append("\n")
      }
    }
    throw EvaluateException(res.toString())
  }
  catch (e: java.lang.Exception) {
    throw EvaluateException(e.message)
  }
  finally {
    if (sourceFile != null) {
      FileUtil.delete(sourceFile)
    }
  }
}

@Throws(IOException::class)
private fun generateTempSourceFile(className: String, sourceCode: String, workingDir: File): File {
  val file = File(workingDir, "debugger/streams/src/$className.java")
  FileUtil.writeToFile(file, sourceCode)
  return file
}

/**
 * Performs loading of argument types into the class loader in [EvaluationContextImpl]
 */
fun Method.prepareArguments(ctx: EvaluationContextImpl) {
  this.argumentTypeNames()
    .minus(PRIMITIVE_TYPE_NAMES)
    .forEach { ctx.loadClass(it) }
}

fun EvaluationContextImpl.loadClass(className: String): ReferenceType? = debugProcess.loadClass(this, className, classLoader)

fun EvaluationContextImpl.loadClassIfAbsent(className: String, bytesLoader: () -> ByteArray?): ReferenceType? {
  try {
    return try {
      this.debugProcess.findClass(this, className, classLoader)
    } catch (e: EvaluateException) {
      if (e.exceptionFromTargetVM!!.type().name() == "java.lang.ClassNotFoundException") {
        val bytes = bytesLoader() ?: return null
        loadAndPrepareClass(this, className, bytes)
      } else {
        throw e
      }
    }
  }
  catch (e: Throwable) {
    return null
  }
}

@Throws(EvaluateException::class)
private fun loadAndPrepareClass(evaluationContext: EvaluationContextImpl, name: String, bytes: ByteArray): ReferenceType? {
  val debugProcess = evaluationContext.debugProcess
  evaluationContext.isAutoLoadClasses = true
  val classLoader = evaluationContext.classLoader
  ClassLoadingUtils.defineClass(name, bytes, evaluationContext, debugProcess, classLoader)
  return try {
    debugProcess.loadClass(evaluationContext, name, classLoader)
  }
  catch (e: Exception) {
    throw EvaluateExceptionUtil.createEvaluateException("Could not load class", e)
  }
}