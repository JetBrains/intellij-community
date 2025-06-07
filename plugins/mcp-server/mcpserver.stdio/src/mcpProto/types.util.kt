package com.intellij.mcpserver.stdio.mcpProto

import com.intellij.mcpserver.stdio.mcpProto.LoggingMessageNotification.SetLevelRequest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

private val logger = KotlinLogging.logger {}

internal object ErrorCodeSerializer : KSerializer<ErrorCode> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.modelcontextprotocol.kotlin.sdk.ErrorCode", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: ErrorCode) {
        encoder.encodeInt(value.code)
    }

    override fun deserialize(decoder: Decoder): ErrorCode {
        val decodedInt = decoder.decodeInt()
        return ErrorCode.Defined.entries.firstOrNull { it.code == decodedInt }
               ?: ErrorCode.Unknown(decodedInt)
    }
}

internal object RequestMethodSerializer : KSerializer<Method> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.modelcontextprotocol.kotlin.sdk.Method", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Method) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): Method {
        val decodedString = decoder.decodeString()
        return Method.Defined.entries.firstOrNull { it.value == decodedString }
               ?: Method.Custom(decodedString)
    }
}

internal object StopReasonSerializer : KSerializer<StopReason> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.modelcontextprotocol.kotlin.sdk.StopReason", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: StopReason) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): StopReason {
        val decodedString = decoder.decodeString()
        return when (decodedString) {
            StopReason.StopSequence.value -> StopReason.StopSequence
            StopReason.MaxTokens.value -> StopReason.MaxTokens
            StopReason.EndTurn.value -> StopReason.EndTurn
            else -> StopReason.Other(decodedString)
        }
    }
}

internal object ReferencePolymorphicSerializer : JsonContentPolymorphicSerializer<Reference>(Reference::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Reference> {
        return when (element.jsonObject.getValue("type").jsonPrimitive.content) {
            ResourceReference.Companion.TYPE -> ResourceReference.Companion.serializer()
            PromptReference.Companion.TYPE -> PromptReference.Companion.serializer()
            else -> UnknownReference.serializer()
        }
    }
}

internal object PromptMessageContentPolymorphicSerializer :
    JsonContentPolymorphicSerializer<PromptMessageContent>(PromptMessageContent::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<PromptMessageContent> {
        return when (element.jsonObject.getValue("type").jsonPrimitive.content) {
            ImageContent.Companion.TYPE -> ImageContent.Companion.serializer()
            TextContent.Companion.TYPE -> TextContent.Companion.serializer()
            EmbeddedResource.Companion.TYPE -> EmbeddedResource.Companion.serializer()
            AudioContent.Companion.TYPE -> AudioContent.Companion.serializer()
            else -> UnknownContent.serializer()
        }
    }
}

internal object PromptMessageContentMultimodalPolymorphicSerializer :
    JsonContentPolymorphicSerializer<PromptMessageContentMultimodal>(PromptMessageContentMultimodal::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<PromptMessageContentMultimodal> {
        return when (element.jsonObject.getValue("type").jsonPrimitive.content) {
            ImageContent.Companion.TYPE -> ImageContent.Companion.serializer()
            TextContent.Companion.TYPE -> TextContent.Companion.serializer()
            AudioContent.Companion.TYPE -> AudioContent.Companion.serializer()
            else -> UnknownContent.serializer()
        }
    }
}

internal object ResourceContentsPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ResourceContents>(ResourceContents::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ResourceContents> {
        val jsonObject = element.jsonObject
        return when {
            jsonObject.contains("text") -> TextResourceContents.serializer()
            jsonObject.contains("blob") -> BlobResourceContents.serializer()
            else -> UnknownResourceContents.serializer()
        }
    }
}

internal fun selectRequestDeserializer(method: String): DeserializationStrategy<Request> {
    selectClientRequestDeserializer(method)?.let { return it }
    selectServerRequestDeserializer(method)?.let { return it }
    return CustomRequest.serializer()
}

internal fun selectClientRequestDeserializer(method: String): DeserializationStrategy<ClientRequest>? {
    return when (method) {
        Method.Defined.Ping.value -> PingRequest.serializer()
        Method.Defined.Initialize.value -> InitializeRequest.serializer()
        Method.Defined.CompletionComplete.value -> CompleteRequest.serializer()
        Method.Defined.LoggingSetLevel.value -> SetLevelRequest.serializer()
        Method.Defined.PromptsGet.value -> GetPromptRequest.serializer()
        Method.Defined.PromptsList.value -> ListPromptsRequest.serializer()
        Method.Defined.ResourcesList.value -> ListResourcesRequest.serializer()
        Method.Defined.ResourcesTemplatesList.value -> ListResourceTemplatesRequest.serializer()
        Method.Defined.ResourcesRead.value -> ReadResourceRequest.serializer()
        Method.Defined.ResourcesSubscribe.value -> SubscribeRequest.serializer()
        Method.Defined.ResourcesUnsubscribe.value -> UnsubscribeRequest.serializer()
        Method.Defined.ToolsCall.value -> CallToolRequest.serializer()
        Method.Defined.ToolsList.value -> ListToolsRequest.serializer()
        else -> null
    }
}

private fun selectClientNotificationDeserializer(element: JsonElement): DeserializationStrategy<ClientNotification>? {
    return when (element.jsonObject.getValue("method").jsonPrimitive.content) {
        Method.Defined.NotificationsCancelled.value -> CancelledNotification.serializer()
        Method.Defined.NotificationsProgress.value -> ProgressNotification.serializer()
        Method.Defined.NotificationsInitialized.value -> InitializedNotification.serializer()
        Method.Defined.NotificationsRootsListChanged.value -> RootsListChangedNotification.serializer()
        else -> null
    }
}

internal object ClientNotificationPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ClientNotification>(ClientNotification::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ClientNotification> {
        return selectClientNotificationDeserializer(element)
               ?: UnknownMethodRequestOrNotification.serializer()
    }
}

internal fun selectServerRequestDeserializer(method: String): DeserializationStrategy<ServerRequest>? {
    return when (method) {
        Method.Defined.Ping.value -> PingRequest.serializer()
        Method.Defined.SamplingCreateMessage.value -> CreateMessageRequest.serializer()
        Method.Defined.RootsList.value -> ListRootsRequest.serializer()
        else -> null
    }
}

internal fun selectServerNotificationDeserializer(element: JsonElement): DeserializationStrategy<ServerNotification>? {
    return when (element.jsonObject.getValue("method").jsonPrimitive.content) {
        Method.Defined.NotificationsCancelled.value -> CancelledNotification.serializer()
        Method.Defined.NotificationsProgress.value -> ProgressNotification.serializer()
        Method.Defined.NotificationsMessage.value -> LoggingMessageNotification.serializer()
        Method.Defined.NotificationsResourcesUpdated.value -> ResourceUpdatedNotification.serializer()
        Method.Defined.NotificationsResourcesListChanged.value -> ResourceListChangedNotification.serializer()
        Method.Defined.ToolsList.value -> ToolListChangedNotification.serializer()
        Method.Defined.PromptsList.value -> PromptListChangedNotification.serializer()
        else -> null
    }
}

internal object ServerNotificationPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ServerNotification>(ServerNotification::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ServerNotification> {
        return selectServerNotificationDeserializer(element)
               ?: UnknownMethodRequestOrNotification.serializer()
    }
}

internal object NotificationPolymorphicSerializer :
    JsonContentPolymorphicSerializer<Notification>(Notification::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Notification> {
        return selectClientNotificationDeserializer(element)
               ?: selectServerNotificationDeserializer(element)
               ?: UnknownMethodRequestOrNotification.serializer()
    }
}

internal object RequestPolymorphicSerializer :
    JsonContentPolymorphicSerializer<Request>(Request::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Request> {
        val method = element.jsonObject.getOrElse("method") { null }?.jsonPrimitive?.content ?: run {
            logger.error { "No method in $element" }
          error("No method in $element")
        }

        return selectClientRequestDeserializer(method)
               ?: selectServerRequestDeserializer(method)
               ?: UnknownMethodRequestOrNotification.serializer()
    }
}

private fun selectServerResultDeserializer(element: JsonElement): DeserializationStrategy<ServerResult>? {
    val jsonObject = element.jsonObject
    return when {
        jsonObject.contains("tools") -> ListToolsResult.serializer()
        jsonObject.contains("resources") -> ListResourcesResult.serializer()
        jsonObject.contains("resourceTemplates") -> ListResourceTemplatesResult.serializer()
        jsonObject.contains("prompts") -> ListPromptsResult.serializer()
        jsonObject.contains("capabilities") -> InitializeResult.serializer()
        jsonObject.contains("description") -> GetPromptResult.serializer()
        jsonObject.contains("completion") -> CompleteResult.serializer()
        jsonObject.contains("toolResult") -> CompatibilityCallToolResult.serializer()
        jsonObject.contains("contents") -> ReadResourceResult.serializer()
        jsonObject.contains("content") -> CallToolResult.serializer()
        else -> null
    }
}

private fun selectClientResultDeserializer(element: JsonElement): DeserializationStrategy<ClientResult>? {
    val jsonObject = element.jsonObject
    return when {
        jsonObject.contains("model") -> CreateMessageResult.serializer()
        jsonObject.contains("roots") -> ListRootsResult.serializer()
        else -> null
    }
}

internal object ServerResultPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ServerResult>(ServerResult::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ServerResult> {
        return selectServerResultDeserializer(element)
               ?: EmptyRequestResult.serializer()
    }
}

internal object ClientResultPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ClientResult>(ClientResult::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ClientResult> {
        return selectClientResultDeserializer(element)
               ?: EmptyRequestResult.serializer()
    }
}

internal object RequestResultPolymorphicSerializer :
    JsonContentPolymorphicSerializer<RequestResult>(RequestResult::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<RequestResult> {
        return selectClientResultDeserializer(element)
               ?: selectServerResultDeserializer(element)
               ?: EmptyRequestResult.serializer()
    }
}

internal object JSONRPCMessagePolymorphicSerializer :
    JsonContentPolymorphicSerializer<JSONRPCMessage>(JSONRPCMessage::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<JSONRPCMessage> {
        val jsonObject = element.jsonObject
        return when {
            jsonObject.contains("message") -> JSONRPCError.serializer()
            !jsonObject.contains("method") -> JSONRPCResponse.serializer()
            jsonObject.contains("id") -> JSONRPCRequest.serializer()
            else -> JSONRPCNotification.serializer()
        }
    }
}

internal val EmptyJsonObject = JsonObject(emptyMap())

public class RequestIdSerializer : KSerializer<RequestId> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RequestId")

    override fun deserialize(decoder: Decoder): RequestId {
        val jsonDecoder = decoder as? JsonDecoder ?: error("Can only deserialize JSON")
        val element = jsonDecoder.decodeJsonElement()

        return when (element) {
            is JsonPrimitive -> when {
                element.isString -> RequestId.StringId(element.content)
                element.longOrNull != null -> RequestId.NumberId(element.long)
                else -> error("Invalid RequestId type")
            }

            else -> error("Invalid RequestId format")
        }
    }

    override fun serialize(encoder: Encoder, value: RequestId) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("Can only serialize JSON")
        when (value) {
            is RequestId.StringId -> jsonEncoder.encodeString(value.value)
            is RequestId.NumberId -> jsonEncoder.encodeLong(value.value)
        }
    }
}

/**
 * Creates a [CallToolResult] with single [TextContent] and [meta].
 */
public fun CallToolResult.Companion.ok(content: String, meta: JsonObject = EmptyJsonObject): CallToolResult =
  CallToolResult(
    content = listOf(TextContent(content)),
    isError = false,
    _meta = meta
  )

/**
 * Creates a [CallToolResult] with single [TextContent] and [meta], with `isError` being true.
 */
public fun CallToolResult.Companion.error(content: String, meta: JsonObject = EmptyJsonObject): CallToolResult =
  CallToolResult(
    content = listOf(TextContent(content)),
    isError = true,
    _meta = meta
  )