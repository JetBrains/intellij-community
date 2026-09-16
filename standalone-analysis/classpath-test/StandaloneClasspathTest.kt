// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.standalone

import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.readLines

fun main(args: Array<String>) {
  val (proguard, classpath, configuration, golden) = args.map { Path.of(it).toAbsolutePath() }

  val jmods = Path.of(System.getProperty("java.home"), "jmods")
  val command = arrayListOf(proguard.toString(), "@$configuration", "-libraryjars", "'$jmods'(!**.jar;!module-info.class)")
  for (line in classpath.readLines()) {
    val (option, file) = line.split(' ', limit = 2)
    command.add(option)
    command.add("'${Path.of(file).toAbsolutePath()}'(!META-INF/versions/**)")
  }

  val stderr = Path.of(System.getenv("TEST_UNDECLARED_OUTPUTS_DIR"), "stderr.txt")

  val exitCode = ProcessBuilder(command)
    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
    .redirectError(stderr.toFile())
    .start()
    .waitFor()

  val stderrText = stderr.readText()

  val actualText = stderrText
    .replace("ProGuard, version [\\d.]+".toRegex(), "")
    .trim()

  val expectedText = golden.readText()
    .trim()

  check(exitCode == 0) { "ProGuard exited with code $exitCode." }

  if (actualText != expectedText) {
    System.err.print(stderrText)
    error("Output differs from the golden file ${golden.name}")
  }
}
