package com.intellij.ide.starter.driver

import com.intellij.driver.client.Remote

@Remote(value = "com.jetbrains.performancePlugin.commands.ReloadFilesCommand",
        plugin = "com.jetbrains.performancePlugin")
interface ReloadFromDiskCommand {
  fun synchronizeFiles(filePaths: List<String>)
}