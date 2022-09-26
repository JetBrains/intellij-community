package com.intellij.settingsSync.plugins

import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.extensions.PluginId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class SettingsSyncPluginsState(val plugins: Map<@Serializable(with=PluginIdSerializer::class) PluginId, PluginData>) {
  @Serializable
  data class PluginData(
    val enabled: Boolean = true,
    val category: SettingsCategory = SettingsCategory.PLUGINS,
    val dependencies: Set<String> = emptySet()
  )
}

internal object PluginIdSerializer : KSerializer<PluginId> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("PluginId", PrimitiveKind.STRING)
  override fun serialize(encoder: Encoder, value: PluginId) = encoder.encodeString(value.idString)
  override fun deserialize(decoder: Decoder): PluginId = PluginId.getId(decoder.decodeString())
}
