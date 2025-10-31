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
import com.intellij.python.sdkConfigurator.common.impl.ModuleDTO
import com.intellij.python.sdkConfigurator.common.impl.ModuleName
import com.intellij.python.sdkConfigurator.common.impl.ToolIdDTO
import com.intellij.python.sdkConfigurator.frontend.PySdkConfiguratorFrontendBundle
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentList
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icon.IconKey


@Composable
internal fun ModuleList(
  moduleItems: PersistentList<ModuleDTO>,
  icons: ImmutableMap<ToolIdDTO, IconKey>,
  checked: SnapshotStateSet<String>,
  onCheck: (ModuleName) -> Unit,
  topLabel: String,
  projectStructureLabel: String,
  environmentLabel: String,
) {
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
        for (module in moduleItems) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = horizontalArrangement) {
            Module(module.name in checked, onCheck, colPadding, module, icons)
          } // Module children
          for (childModule in module.childModules) {
            CheckboxRow(
              childModule,
              childModule in checked,
              onCheckedChange = {},
              softWrap = false,
              maxLines = 1,
              enabled = false,
              modifier = Modifier.padding(start = colPadding + 26.dp) // Checkbox width
            )
          }
        }
      }
    }
  }
}

@Composable
private fun RowScope.Module(
  checked: Boolean,
  onCheck: (ModuleName) -> Unit,
  columnPadding: Dp,
  moduleInfo: ModuleDTO, // For parent only
  icons: ImmutableMap<ToolIdDTO, IconKey>,
) {
  val colModifier = Modifier.weight(1f)
  val newText = remember { PySdkConfiguratorFrontendBundle.message("python.sdk.configurator.frontend.choose.modules.new") }
  val moduleName = moduleInfo.name
  CheckboxRow(
    moduleName,
    softWrap = false,
    maxLines = 1,
    checked = checked,
    onCheckedChange = {
      onCheck(moduleName)
    },
    modifier = colModifier
  )

  Row(colModifier.padding(start = columnPadding)) {
    val text = moduleInfo.existingPyVersion?.let { PySdkConfiguratorFrontendBundle.message("python.sdk.configurator.frontend.choose.modules.workspace.existing", it) }
               ?: newText
    val icon = icons[moduleInfo.createdByTool]
    if (icon != null) {
      Icon(icon, text)
    }
    Text(text, Modifier.clickable(onClick = { onCheck(moduleName) }), maxLines = 1, softWrap = false)
  }
}
