package circlet.client

import circlet.platform.api.*
import circlet.platform.client.*
import klogging.*

private val LOG = KLoggers.logger("circlet.client.UtilitiesKt")

fun <T: ARecord> Ref<T>.safeResolve(client: KCircletClient) : T? =
    try {
        resolve(client)
    }
    catch (t: Throwable) {
        LOG.error(t) { "Could not resolve $this" }

        null
    }
