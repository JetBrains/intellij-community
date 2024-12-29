// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package intellij.settingsSync.fileSystem

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.settingsSync.communicator.SettingsSyncUserData
import com.intellij.util.containers.mapSmart
import com.intellij.util.containers.toMutableSmartList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

internal class FSAuthService : SettingsSyncAuthService {
  override val providerCode: String
    get() = "fs"
  override val providerName: String
    get() = "File System"

  override val icon: Icon?
    get() = com.intellij.icons.AllIcons.Actions.ModuleDirectory

  override suspend fun login(): Deferred<SettingsSyncUserData?> {
    val retval = CompletableDeferred<SettingsSyncUserData>()
    ApplicationManager.getApplication().invokeLater {
      val folderDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        .withTitle("Select root folder")
      val chooser = FileChooserFactory.getInstance().createPathChooser(folderDescriptor, null, null)
      chooser.choose(null) {
        val path = it.single().path
        val userData = userDataFromPath(path)
        val availableAccounts = (PropertiesComponent.getInstance().getList("FSAuthServiceAccounts") ?: emptyList()).toMutableSmartList()
        if (!availableAccounts.contains(path)) {
          availableAccounts.add(path)
          PropertiesComponent.getInstance().setList("FSAuthServiceAccounts", availableAccounts)
        }
        retval.complete(userData)
      }
    }
    return retval
  }

  override fun getUserData(userId: String): SettingsSyncUserData? {
    return getAvailableUserAccounts().find { it.id == userId }
  }

  override fun getAvailableUserAccounts(): List<SettingsSyncUserData> {
    return PropertiesComponent.getInstance().getList("FSAuthServiceAccounts")?.mapSmart { userDataFromPath(it) } ?: emptyList()
  }

  private fun userDataFromPath(pathStr: String) : SettingsSyncUserData {
    val path = Path.of(pathStr)
    return SettingsSyncUserData(
      path.absolutePathString(),
      providerCode,
      path.name,
      "noname@email.com"
    )
  }

}
