package org.jetbrains.idea.packagesearch.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.*
import io.ktor.serialization.ContentConverter
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.util.StringValuesBuilder
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.core.readText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull
import org.jetbrains.packagesearch.api.v2.ApiPackagesResponse
import org.jetbrains.packagesearch.api.v2.ApiStandardPackage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal object PackageSearchApiContentTypes {
  val StandardV2
    get() = ContentType("application", "vnd.jetbrains.packagesearch.standard.v2+json")
  val MinimalV2
    get() = ContentType("application", "vnd.jetbrains.packagesearch.minimal.v2+json")
}

internal var HttpTimeout.HttpTimeoutCapabilityConfiguration.requestTimeout: Duration?
  get() = connectTimeoutMillis?.milliseconds
  set(value) {
    connectTimeoutMillis = value?.inWholeMilliseconds
  }

@Suppress("UnusedReceiverParameter")
internal val ContentType.Application.PackageSearch
  get() = PackageSearchApiContentTypes

internal val emptyStandardV2PackagesWithRepos = ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>(
  packages = emptyList(),
  repositories = emptyList()
)

internal fun ContentNegotiation.Config.packageSearch(json: Json = Json) {
  register(ContentType.Application.PackageSearch.StandardV2, KotlinxSerializationConverter(json))
}

internal fun StringValuesBuilder.append(name: String, value: Iterable<Any>) = append(name, value.joinToString(","))

internal fun StringValuesBuilder.append(name: String, value: Any?) = append(name, value.toString())
internal fun URLBuilder.parameters(config: ParametersBuilder.() -> Unit) {
  parameters.apply(config)
}

internal fun HttpMessageBuilder.headers(headers: Headers) {
  headers {
    headers.forEach { s, strings -> appendAll(s, strings) }
  }
}

internal fun Logger.Companion.simple(log: (String) -> Unit) = object : Logger {
  override fun log(message: String) = log(message)
}

internal fun buildHeaders(size: Int, build: HeadersBuilder.() -> Unit) = HeadersBuilder(size).apply(build).build()

internal suspend inline fun <reified T : Any> HttpClient.getBody(config: HttpRequestBuilder.() -> Unit): T =
  get(config).body()