package com.intellij.mcpserver

import kotlin.coroutines.CoroutineContext

class ClientInfo(val name: String, val version: String)

internal class ClientInfoElement(val info: ClientInfo?) : CoroutineContext.Element {
  companion object Key : CoroutineContext.Key<ClientInfoElement>
  override val key: CoroutineContext.Key<*> = Key
}

val CoroutineContext.clientInfoOrNull: ClientInfo? get() = get(ClientInfoElement.Key)?.info
val CoroutineContext.clientInfo: ClientInfo get() = get(ClientInfoElement.Key)?.info ?: ClientInfo("Unknown client", "Unknown version")