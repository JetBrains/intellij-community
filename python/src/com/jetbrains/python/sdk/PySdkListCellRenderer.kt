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
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.LayeredIcon
import com.intellij.ui.ListCellRendererWrapper
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
      SEPARATOR -> ListCellRendererWrapper.createSeparator(null)
      else -> super.getListCellRendererComponent(list, value, index, selected, hasFocus)
    }

  override fun customizeCellRenderer(list: JList<out Any>, value: Any?, index: Int, selected: Boolean, hasFocus: Boolean) {
    when (value) {
      is Sdk -> {
        val flavor = PythonSdkFlavor.getPlatformIndependentFlavor(value.homePath)
        val icon = if (flavor != null) flavor.icon else (value.sdkType as SdkType).icon
        icon?.let {
          setIcon(customizeIcon(value, it))
        }
        val name = sdkModifiers?.get(value)?.name ?: value.name
        append(customizeName(value, name))
        setToolTipText(value.homePath)
      }
      is String -> append(value)
      null -> append("<No interpreter>")
    }
  }

  companion object {
    const val SEPARATOR = "separator"

    private fun customizeName(sdk: Sdk, name: String): String =
      when {
        PythonSdkType.isInvalid(sdk) || PythonSdkType.hasInvalidRemoteCredentials(sdk) -> "[invalid] $name"
        PythonSdkType.isIncompleteRemote(sdk) -> "[incomplete] $name"
        else -> name
      }

    private fun customizeIcon(sdk: Sdk, icon: Icon): Icon =
      when {
        PythonSdkType.isInvalid(sdk) || PythonSdkType.isIncompleteRemote(sdk) || PythonSdkType.hasInvalidRemoteCredentials(sdk) ->
          wrapIconWithWarningDecorator(icon)
        sdk is PyDetectedSdk ->
          IconLoader.getTransparentIcon(icon)
        else ->
          icon
      }

    private fun wrapIconWithWarningDecorator(icon: Icon): LayeredIcon =
      LayeredIcon(2).apply {
        setIcon(icon, 0)
        setIcon(AllIcons.Actions.Cancel, 1)
      }
  }
}
