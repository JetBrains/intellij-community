package com.intellij.ide.starter.wsl

import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.logOutput
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkPredicate

class WslDistributionNotFoundException(private val jdkPredicate: JdkPredicate? = null) : Exception() {
  override val message: String
    get() {
      val red = "\u001b[31m"
      val reset = "\u001b[0m"
      logOutput("\n\n\n\n\n")
      logOutput("=======================================================================================")
      logOutput("****                                                                               ****")
      logOutput("****   $red   WARNING         WARNING         WARNING      $reset                            ****")
      logOutput("****                                                                               ****")
      logOutput("****      You are trying to execute tests with WSL                                 ****")
      logOutput("****      You have no WSL installed distributions                                  ****")
      logOutput("****      Please follow the installation guide to install it:                      ****")
      logOutput("****         English: https://docs.microsoft.com/en-us/windows/wsl/install-win10   ****")
      logOutput("****         Russian: https://docs.microsoft.com/ru-ru/windows/wsl/install-win10   ****")
      logOutput("****                                                                               ****")
      logOutput("****                                                                               ****")
      logOutput("****    $red  WARNING         WARNING         WARNING     $reset                             ****")
      logOutput("****                                                                               ****")
      logOutput("=======================================================================================")
      logOutput("\n\n\n\n\n")

      return buildString {
        appendLine("\n")
        appendLine("Can't run test on WSL")
        appendLine("Current OS: ${SystemInfo.OS_NAME} ${SystemInfo.OS_VERSION}")
        appendLine("WSL distributions installed: ${WslDistributionManager.getInstance().installedDistributions}")
        jdkPredicate?.let { appendLine(jdkPredicate) }
      }
    }
}