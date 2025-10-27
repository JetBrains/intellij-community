package com.intellij.python.sdkConfigurator.frontend.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.intellij.python.sdkConfigurator.common.impl.CreateSdkDTO
import com.intellij.python.sdkConfigurator.common.impl.ToolIdDTO
import com.intellij.python.sdkConfigurator.frontend.PySdkConfiguratorFrontendBundle
import kotlinx.collections.immutable.ImmutableMap
import org.jetbrains.annotations.Nls
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icon.IconKey


@Composable
internal fun ModuleList(
  moduleItems: ImmutableMap<String, CreateSdkDTO>,
  icons: ImmutableMap<ToolIdDTO, IconKey>,
  checked: SnapshotStateSet<String>,
  onCheckChange: (String, Boolean) -> Unit,
  topLabel: @Nls String,
  projectStructureLabel: @Nls String,
  environmentLabel: @Nls String,
) {
  val newText = remember { PySdkConfiguratorFrontendBundle.message("python.sdk.configurator.frontend.choose.modules.new") }
  val ts = JewelTheme.defaultTextStyle
  val padding = 2.dp
  val longestItemChars = remember { (listOf(projectStructureLabel) + moduleItems.keys).maxBy { it.length } }
  val leftColumnMinSize = measureText(longestItemChars, ts)
  val spaceBetweenCols = 16.dp
  Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(padding)) {
    Text(topLabel, Modifier.padding(bottom = 10.dp))
    Row(Modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
      Text(projectStructureLabel, Modifier.width(leftColumnMinSize + spaceBetweenCols + padding + 26.dp)) //~ checkbox size
      Text(environmentLabel, textAlign = TextAlign.End)
    }
    val moduleItems = remember { moduleItems.entries.sortedBy { it.key } }
    VerticallyScrollableContainer {
      Column(Modifier, horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(padding)) {
        for ((moduleName, moduleInfo) in moduleItems) {
          val parent = when (moduleInfo) {
            is CreateSdkDTO.ConfigurableModule -> null
            is CreateSdkDTO.SameAs -> moduleInfo.parentModuleName
          }
          Row(Modifier.padding(padding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spaceBetweenCols)) {
            val checked = moduleName in checked
            val elementToChange = parent ?: moduleName // TODO: move logic out of UI
            CheckboxRow(
              moduleName,
              checked = checked,
              onCheckedChange = {
                onCheckChange(elementToChange, it)
              },
              textStyle = ts,
              textModifier = Modifier.width(leftColumnMinSize)
            )
            val text = when (moduleInfo) {
              is CreateSdkDTO.ConfigurableModule -> {
                val text = moduleInfo.existingVersion?.let { PySdkConfiguratorFrontendBundle.message("python.sdk.configurator.frontend.choose.modules.workspace.existing", it) }
                           ?: newText
                val icon = icons[moduleInfo.createdByTool]
                if (icon != null) {
                  Icon(icon, text)
                }
                text
              }
              is CreateSdkDTO.SameAs -> {
                PySdkConfiguratorFrontendBundle.message("python.sdk.configurator.frontend.choose.modules.workspace.member", moduleInfo.parentModuleName)
              }
            }
            Text(text, Modifier.fillMaxWidth())
          }
        }
      }
    }
  }
}

@Composable
private fun measureText(text: String, textStyle: TextStyle): Dp {
  val textMeasurer = rememberTextMeasurer()
  return with(LocalDensity.current) {
    textMeasurer.measure(text, textStyle).size.width.toDp()
  }
}