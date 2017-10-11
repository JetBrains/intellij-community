/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.sdk

import com.intellij.icons.AllIcons
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.LayeredIcon
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TitledSeparator
import com.intellij.util.ui.JBUI
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import java.awt.Component
import javax.swing.Icon
import javax.swing.JList

/**
 * @author vlan
 */
open class PySdkListCellRenderer(private val sdkModifiers: Map<Sdk, SdkModificator>?) : ColoredListCellRenderer<Any>() {
  override fun getListCellRendererComponent(list: JList<out Any>?, value: Any?, index: Int, selected: Boolean,
                                            hasFocus: Boolean): Component =
    when (value) {
      SEPARATOR -> TitledSeparator(null).apply {
        border = JBUI.Borders.empty()
      }
      else -> super.getListCellRendererComponent(list, value, index, selected, hasFocus)
    }

  override fun customizeCellRenderer(list: JList<out Any>, value: Any?, index: Int, selected: Boolean, hasFocus: Boolean) {
    when (value) {
      is Sdk -> {
        appendName(value)
        icon = customizeIcon(value)
      }
      is String -> append(value)
      null -> append("<No interpreter>")
    }
  }

  private fun appendName(sdk: Sdk) {
    val name = sdkModifiers?.get(sdk)?.name ?: sdk.name
    when {
      PythonSdkType.isInvalid(sdk) || PythonSdkType.hasInvalidRemoteCredentials(sdk) ->
        append("[invalid] $name", SimpleTextAttributes.ERROR_ATTRIBUTES)
      PythonSdkType.isIncompleteRemote(sdk) ->
        append("[incomplete] $name", SimpleTextAttributes.ERROR_ATTRIBUTES)
      else ->
        append(name)
    }
    val homePath = sdk.homePath
    val relHomePath = homePath?.let { FileUtil.getLocationRelativeToUserHome(it) }
    if (relHomePath != null && homePath !in name && relHomePath !in name) {
      append(" $relHomePath", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
  }

  companion object {
    const val SEPARATOR = "separator"

    private fun customizeIcon(sdk: Sdk): Icon? {
      val flavor = PythonSdkFlavor.getPlatformIndependentFlavor(sdk.homePath)
      val icon = if (flavor != null) flavor.icon else (sdk.sdkType as? SdkType)?.icon ?: return null
      return when {
        PythonSdkType.isInvalid(sdk) || PythonSdkType.isIncompleteRemote(sdk) || PythonSdkType.hasInvalidRemoteCredentials(sdk) ->
          wrapIconWithWarningDecorator(icon)
        sdk is PyDetectedSdk ->
          IconLoader.getTransparentIcon(icon)
        else ->
          icon
      }
    }

    private fun wrapIconWithWarningDecorator(icon: Icon): LayeredIcon =
      LayeredIcon(2).apply {
        setIcon(icon, 0)
        setIcon(AllIcons.Actions.Cancel, 1)
      }
  }
}
