package com.intellij.python.sdkConfigurator.frontend.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.intellij.python.sdkConfigurator.common.impl.ModuleDTO
import com.intellij.python.sdkConfigurator.common.impl.ModuleName
import com.intellij.python.sdkConfigurator.common.impl.ToolIdDTO
import com.intellij.python.sdkConfigurator.frontend.PySdkConfiguratorFrontendBundle
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentList
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.theme.dividerStyle


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
  VerticallyScrollableContainer {
    val colPadding = 2.dp
    val horizontalArrangement = Arrangement.spacedBy(colPadding)
    Column(Modifier.width(IntrinsicSize.Max)
             .padding(end = scrollbarContentSafePadding())
             .border(Stroke.Alignment.Inside, JewelTheme.dividerStyle.metrics.thickness, JewelTheme.dividerStyle.color)
             .padding(5.dp)

    ) {
      Text(text = topLabel, maxLines = 1, softWrap = false, modifier = Modifier
        .padding(bottom = 10.dp)
      )
      Divider(Orientation.Horizontal, thickness = 5.dp)
      Row {
        val modifier = Modifier.weight(1f)
        Text(projectStructureLabel, modifier = modifier)
        Divider(Orientation.Vertical)
        Text(environmentLabel, modifier = modifier)
      }
      Divider(Orientation.Horizontal)
      val hide = Modifier.alpha(0f)
      for (module in moduleItems) {
        var subModuleOpened by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = horizontalArrangement) {
          OpenArrow(subModuleOpened, { subModuleOpened = !subModuleOpened },
                    modifier = if (module.childModules.isEmpty()) hide else Modifier)
          Module(module.name in checked, onCheck, colPadding, module, icons)
        } // Module children

        for (childModule in module.childModules) {
          Row(Modifier.alpha(if (subModuleOpened) 1f else 0f)) {
            // Invisible elements are for padding only: will replace to the real padding
            OpenArrow(false, {}, hide)
            Checkbox(false, onCheckedChange = {}, modifier = hide)
            CheckboxRow(
              childModule,
              childModule in checked,
              onCheckedChange = {},
              softWrap = false,
              maxLines = 1,
              enabled = false,
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
  moduleInfo: ModuleDTO,
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

