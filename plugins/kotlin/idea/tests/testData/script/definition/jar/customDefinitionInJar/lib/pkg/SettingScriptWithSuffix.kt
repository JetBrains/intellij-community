package pkg

import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.CompiledScript

@KotlinScript(
    displayName = "Custom Setting Script",
    fileExtension = "custom.settings.kts",
)
class SettingScriptWithSuffix(private val info: CompiledScript? = null) {
}