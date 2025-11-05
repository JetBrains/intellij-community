package com.intellij.python.sdkConfigurator.frontend.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.intellij.python.sdkConfigurator.common.impl.ModuleDTO
import com.intellij.python.sdkConfigurator.common.impl.ModuleName
import com.intellij.python.sdkConfigurator.common.impl.ToolIdDTO
import com.intellij.python.sdkConfigurator.frontend.ModulesViewModel
import com.intellij.python.sdkConfigurator.frontend.PySdkConfiguratorFrontendBundle
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.FlowPreview
import org.jetbrains.annotations.Nls
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.component.styling.LocalCheckboxStyle
import org.jetbrains.jewel.ui.icon.IconKey

/**
 * List of modules from [viewModel]
 */
@OptIn(FlowPreview::class)
@Composable
internal fun ModuleList(
  viewModel: ModulesViewModel,
  // UI labels
  topLabel: @Nls String,
  projectStructureLabel: @Nls String,
  environmentLabel: @Nls String,
) {
  LaunchedEffect(viewModel) {
    viewModel.processFilterUpdates()
  }
  val space = 5.dp
  VerticallyScrollableContainer {
    val checkboxArrangement = Arrangement.spacedBy(space)
    Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = checkboxArrangement) {
      Text(text = topLabel, Modifier.padding(end = space, bottom = space))

      Column(Modifier.padding(space).border(Stroke.Alignment.Outside, 1.dp, JewelTheme.globalColors.borders.normal).padding(space).fillMaxWidth(), verticalArrangement = checkboxArrangement) {
        Row(Modifier.height(IntrinsicSize.Min).padding(start = space, bottom = space, end = space), horizontalArrangement = checkboxArrangement) {
          ModuleRow(
            left = { modifier ->
              Row(modifier) {
                Text(projectStructureLabel)
                Spacer(Modifier.width(1.dp))
                TextField(viewModel.moduleFilter, undecorated = true, modifier = Modifier.weight(1f))
              }
            },
            right = { modifier ->
              Text(environmentLabel, modifier = modifier)
            }
          )
        }
        for (module in viewModel.filteredModules) {
          Row(verticalAlignment = Alignment.Top, horizontalArrangement = checkboxArrangement, modifier = Modifier.padding(horizontal = space)) {
            Module(module.name in viewModel.checkedModules, viewModel::clicked, module, viewModel.icons, checkboxArrangement)
          }
        }
      }
    }
  }
}

/**
 * Renders [module]
 */
@Composable
private fun Module(
  checked: Boolean,
  onCheck: (ModuleName) -> Unit,
  module: ModuleDTO,
  icons: ImmutableMap<ToolIdDTO, IconKey>,
  checkBoxArrangement: Arrangement.HorizontalOrVertical,
) {
  var subModuleOpened by remember { mutableStateOf(false) }
  val newText = remember { PySdkConfiguratorFrontendBundle.message("python.sdk.configurator.frontend.choose.modules.new") }
  val moduleName = module.name
  val checkBoxWidth = Modifier.padding(start = LocalCheckboxStyle.current.metrics.checkboxSize.width)

  Row(verticalAlignment = Alignment.Top, horizontalArrangement = checkBoxArrangement) {
    ModuleRow(
      left = { modifier ->
        Row(modifier) {
          // TODO: Get real size instead of "invisible" checkbox
          OpenArrow(subModuleOpened, { subModuleOpened = !subModuleOpened },
                    modifier = if (module.childModules.isEmpty()) {
                      Modifier.alpha(0f)
                    }
                    else Modifier)
          Column(verticalArrangement = checkBoxArrangement) {
            CheckboxRow(
              moduleName,
              softWrap = false,
              maxLines = 1,
              checked = checked,
              onCheckedChange = {
                onCheck(moduleName)
              },
            )
            if (subModuleOpened) {
              for (childModule in module.childModules) {
                Row {
                  CheckboxRow(
                    childModule,
                    checked,
                    onCheckedChange = {},
                    softWrap = false,
                    maxLines = 1,
                    enabled = false,
                    modifier = checkBoxWidth
                  )
                }
              }
            }
          }
        }
      },
      right = { modifier ->
        Row(modifier) {
          val text = module.existingPyVersion?.let { PySdkConfiguratorFrontendBundle.message("python.sdk.configurator.frontend.choose.modules.workspace.existing", it) }
                     ?: newText
          val icon = icons[module.createdByTool]
          if (icon != null) {
            Icon(icon, text)
          }
          Text(text, Modifier.clickable(onClick = { onCheck(moduleName) }), maxLines = 1, softWrap = false)
        }
      })
  }
}

@Composable
private fun RowScope.ModuleRow(left: @Composable (modifier: Modifier) -> Unit, right: @Composable (modifier: Modifier) -> Unit) {
  val modifier = Modifier.weight(1f)
  left(modifier)
  Divider(Orientation.Vertical, color = JewelTheme.globalColors.borders.normal, thickness = 1.dp, modifier = Modifier.fillMaxHeight())
  right(modifier)
}