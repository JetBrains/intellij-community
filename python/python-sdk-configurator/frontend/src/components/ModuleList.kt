package com.intellij.python.sdkConfigurator.frontend.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.sdkConfigurator.common.impl.CreateSdkDTO
import com.intellij.python.sdkConfigurator.common.impl.ModuleName
import com.intellij.python.sdkConfigurator.common.impl.ToolIdDTO
import com.intellij.python.sdkConfigurator.frontend.PySdkConfiguratorFrontendBundle
import kotlinx.collections.immutable.ImmutableMap
import org.jetbrains.annotations.Nls
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icon.IconKey


@Composable
internal fun ModuleList(
  moduleItems: ImmutableMap<ModuleName, CreateSdkDTO>,
  icons: ImmutableMap<ToolIdDTO, IconKey>,
  checked: SnapshotStateSet<String>,
  onCheck: (ModuleName) -> Unit,
  topLabel: @Nls String,
  projectStructureLabel: @Nls String,
  environmentLabel: @Nls String,
) {
  val moduleItems = remember { moduleItems.entries.sortedBy { it.key } }
  Box {
    VerticallyScrollableContainer {
      val colPadding = 2.dp
      val horizontalArrangement = Arrangement.spacedBy(colPadding)
      Column(Modifier.width(IntrinsicSize.Max).height(IntrinsicSize.Max).padding(end = scrollbarContentSafePadding())) {
        Text(text = topLabel)
        Divider(Orientation.Horizontal, thickness = 5.dp)
        Row {
          val modifier = Modifier.weight(1f)
          Text(projectStructureLabel, modifier = modifier)
          Divider(Orientation.Vertical)
          Text(environmentLabel, modifier = modifier)
        }
        Divider(Orientation.Horizontal)
        for ((moduleName, moduleInfo) in moduleItems) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = horizontalArrangement) {
            Module(moduleName, moduleName in checked, onCheck, colPadding, moduleInfo, icons)
          }
        }
      }
    }
  }
}

@Composable
private fun RowScope.Module(
  moduleName: @NlsSafe String,
  checked: Boolean,
  onCheck: (ModuleName) -> Unit,
  columnPadding: Dp,
  moduleInfo: CreateSdkDTO,
  icons: ImmutableMap<ToolIdDTO, IconKey>,
) {
  val colModifier = Modifier.weight(1f)
  val newText = remember { PySdkConfiguratorFrontendBundle.message("python.sdk.configurator.frontend.choose.modules.new") }
  val parent = when (moduleInfo) {
    is CreateSdkDTO.ConfigurableModule -> null
    is CreateSdkDTO.SameAs -> moduleInfo.parentModuleName
  }
  val elementToChange = parent ?: moduleName // TODO: move logic out of UI
  CheckboxRow(
    moduleName,
    softWrap = false,
    maxLines = 1,
    checked = checked,
    onCheckedChange = {
      onCheck(elementToChange)
    },
    modifier = colModifier
  )
  Row(colModifier.padding(start = columnPadding)) {
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
    Text(text, Modifier.clickable(onClick = { onCheck(elementToChange) }), maxLines = 1, softWrap = false)
  }
}
