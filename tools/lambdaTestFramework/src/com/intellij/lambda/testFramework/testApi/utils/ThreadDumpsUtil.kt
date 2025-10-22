package com.intellij.lambda.testFramework.testApi.utils

import com.intellij.diagnostic.ThreadDumper
import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.openapi.application.PathManager
import com.intellij.remoteDev.tests.impl.utils.getArtifactsFileName
import java.io.File

fun dumpThreads(specificName: String) {
  frameworkLogger.info("Dump threads '$specificName'")
  val fileName = getArtifactsFileName(specificName, "ThreadDumps", "log") // todo add tred dump prefix to constants
  val threadDumpFile = File(PathManager.getLogPath()).resolve(fileName)
  threadDumpFile.writeText(ThreadDumper.getThreadDumpInfo(ThreadDumper.getThreadInfos(), false).rawDump)
}