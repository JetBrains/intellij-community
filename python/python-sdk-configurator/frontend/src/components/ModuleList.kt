package com.intellij.python.sdkConfigurator.frontend.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.python.sdkConfigurator.frontend.ModuleInfo
import kotlinx.collections.immutable.ImmutableMap
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer


@Composable
internal fun ModuleList(
  moduleItems: ImmutableMap<String, ModuleInfo>,
  checked: SnapshotStateSet<String>,
  onCheckChange: (String, Boolean) -> Unit,
) {
  VerticallyScrollableContainer {
    Column(Modifier.width(500.dp)) {
      for ((moduleName, moduleInfo) in moduleItems) {
        val (parent, pythons) = moduleInfo
        Row(Modifier.padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
          val checked = moduleName in checked
          val elementToChange = parent ?: moduleName
          Checkbox(
            checked = checked,
            onCheckedChange = {
              onCheckChange(elementToChange, it)
            },
          )
          Text(moduleName, Modifier.padding(start = 1.dp).clickable(true, onClick = { onCheckChange(elementToChange, !checked) }))
          Spacer(Modifier.weight(1f))
          PythonsDropDown(pythons, Modifier.width(200.dp))
        }
      }
    }
  }
}
