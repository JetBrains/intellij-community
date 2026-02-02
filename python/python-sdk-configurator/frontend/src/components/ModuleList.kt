package com.intellij.python.sdkConfigurator.frontend.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import com.intellij.python.sdkConfigurator.common.impl.ModuleDTO
import com.intellij.python.sdkConfigurator.common.impl.ModuleName
import com.intellij.python.sdkConfigurator.common.impl.ToolIdDTO
import com.intellij.python.sdkConfigurator.frontend.ModulesViewModel
import com.intellij.python.sdkConfigurator.frontend.PySdkConfiguratorFrontendBundle.message
import kotlinx.collections.immutable.ImmutableMap
import org.jetbrains.jewel.bridge.icon.fromPlatformIcon
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.InfoText
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.TriStateCheckboxRow
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.styling.LocalCheckboxStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.IntelliJIconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * List of modules from [viewModel]. Screen sizes are in physical pixels
 */
@Composable
internal fun ModuleList(
  viewModel: ModulesViewModel,
) {
  LaunchedEffect(viewModel) {
    viewModel.processFilterUpdates()
  }
  val topLabel = remember { message("python.sdk.configurator.frontend.choose.modules.text") }
  val searchTitle = remember { message("python.sdk.configurator.frontend.choose.modules.project.search") }
  val environmentLabel = remember { message("python.sdk.configurator.frontend.choose.modules.environment") }
  val selectAllLabel = remember { message("python.sdk.configurator.frontend.choose.modules.select.all") }

  val border = Modifier.border(Stroke.Alignment.Outside, 1.dp, JewelTheme.globalColors.borders.normal)
  val space = 5.dp
  VerticallyScrollableContainer(Modifier.padding(space).then(border).fillMaxSize()) {
    val checkBoxArrangement = Arrangement.spacedBy(space)
    Column(Modifier.fillMaxSize(), verticalArrangement = checkBoxArrangement) {
      Text(text = topLabel, Modifier.padding(space))


      Row(Modifier.fillMaxSize().then(border).padding(space), horizontalArrangement = checkBoxArrangement, verticalAlignment = Alignment.CenterVertically) {
        ModuleRow(
          left = { modifier ->
            Row(modifier) {
              TextField(viewModel.moduleFilter, undecorated = false, modifier = Modifier.weight(1f), leadingIcon = { Icon(AllIconsKeys.Actions.Find, searchTitle) })
            }
          },
          right = { modifier ->
            Text(environmentLabel, modifier = modifier)
          },
        )
      }
      Column(Modifier.fillMaxSize(), verticalArrangement = checkBoxArrangement) {
        Row(verticalAlignment = Alignment.Top) {
          // TODO: Get real size instead of "invisible" checkbox
          OpenArrow(false, {}, Modifier.alpha(0.0f))
          TriStateCheckboxRow(selectAllLabel, viewModel.selectAllState, viewModel::selectAllClicked)
          Spacer(Modifier.weight(1f))
        }
        for (module in viewModel.filteredParentModules) {
          key(module.name) {
            Module(module.name in viewModel.checkedModules, viewModel::moduleClicked, module, viewModel.icons, checkBoxArrangement)
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
  val newText = remember { message("python.sdk.configurator.frontend.choose.modules.new") }
  val moduleName = module.name
  val checkBoxWidth = Modifier.padding(start = LocalCheckboxStyle.current.metrics.checkboxSize.width)
  val moduleIcon = remember { IntelliJIconKey.fromPlatformIcon(AllIcons.Nodes.Module) }

  Row(verticalAlignment = Alignment.Top, horizontalArrangement = checkBoxArrangement) {
    ModuleRow(
      left = { modifier ->
        Row(modifier, verticalAlignment = if (subModuleOpened) Alignment.Top else Alignment.CenterVertically) {
          // TODO: Get real size instead of "invisible" checkbox
          OpenArrow(subModuleOpened, { subModuleOpened = !subModuleOpened },
                    modifier = if (module.childModules.isEmpty()) {
                      Modifier.alpha(0f)
                    }
                    else Modifier)
          Column(verticalArrangement = checkBoxArrangement) {
            CheckboxRow(
              checked = checked,
              onCheckedChange = {
                onCheck(moduleName)
              },
              content = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                  Icon(moduleIcon, module.path)
                  Text(moduleName, softWrap = false, maxLines = 1)
                  module.path?.let { path ->
                    InfoText(path, softWrap = false, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  }
                }
              }
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
        Row(modifier, horizontalArrangement = checkBoxArrangement) {
          val text = module.existingPyVersion?.let { message("python.sdk.configurator.frontend.choose.modules.workspace.existing", it) }
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
