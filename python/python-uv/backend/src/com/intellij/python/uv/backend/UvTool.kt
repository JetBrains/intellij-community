package com.intellij.python.uv.backend

import com.intellij.ide.util.PropertiesComponent
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.jetbrains.python.sdk.ToolCommandExecutor
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.pathString

@ApiStatus.Internal
val UV_TOOL: ToolCommandExecutor = ToolCommandExecutor(
  "uv",
  getToolPathFromSettings = { uvPath?.pathString }
)

@ApiStatus.Internal
fun setUvExecutableLocal(localUvPath: Path) {
  assert(localUvPath.getEelDescriptor() == LocalEelDescriptor) { "Path $localUvPath is not local" }
  PropertiesComponent.getInstance().uvPath = localUvPath
}

// impl

private const val UV_PATH_SETTING: String = "PyCharm.Uv.Path"
private var PropertiesComponent.uvPath: Path?
  get() {
    return getValue(UV_PATH_SETTING)?.let { Path.of(it) }
  }
  set(value) {
    setValue(UV_PATH_SETTING, value.toString())
  }

