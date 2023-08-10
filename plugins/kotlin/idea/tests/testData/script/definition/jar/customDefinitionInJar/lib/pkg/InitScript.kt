package pkg

import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.CompiledScript

@KotlinScript(
    displayName = "Custom Init Script",
    fileExtension = "custom.init.kts",
)
class InitScript(private val info: CompiledScript? = null) {
}