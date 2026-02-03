package com.jetbrains.python.sdk

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.jetbrains.python.sdk.legacy.PythonSdkUtil

var Module.pythonSdk: Sdk?
  get() = PythonSdkUtil.findPythonSdk(this)
  set(newSdk) {
    val prevSdk = pythonSdk
    thisLogger.info("Setting PythonSDK $newSdk to module $this")
    ModuleRootModificationUtil.setModuleSdk(this, newSdk)
    runInEdt {
      DaemonCodeAnalyzer.getInstance(project).restart("Setting PythonSDK $newSdk to module $this")
    }
    ApplicationManager.getApplication().messageBus.syncPublisher(PySdkListener.TOPIC).moduleSdkUpdated(this, prevSdk, newSdk)
  }


private val thisLogger = fileLogger()
