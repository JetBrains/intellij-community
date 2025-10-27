package com.intellij.lambda.testFramework.testApi

import com.intellij.configurationStore.saveSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.remoteDev.tests.LambdaIdeContext

context(lambdaIdeContext: LambdaIdeContext)
fun saveApplicationSettings() {
  runWithModalProgressBlocking(getProject(), "Save") {
    saveSettings(ApplicationManager.getApplication(), forceSavingAllSettings = true)
  }
}
