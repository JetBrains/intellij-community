package com.intellij.python.sdkConfigurator.frontend.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.python.sdkConfigurator.frontend.Status
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer


@Composable
internal fun CheckboxList(
  items: ImmutableList<Pair<String, Status>>,
  checked: SnapshotStateSet<String>,
  onCheckChange: (String, Boolean) -> Unit,
) {
  VerticallyScrollableContainer {
    Column {
      for ((item, status) in items) {
        Row(Modifier.padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
          val checked = item in checked
          Checkbox(checked = checked, onCheckedChange = { onCheckChange(item, it) }, enabled = status == Status.ENABLED)
          Text(item, Modifier.padding(start = 1.dp).clickable(true, onClick = { onCheckChange(item, !checked) }))
        }
      }
    }
  }
}