// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.common.io.Resources
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Version
import com.intellij.util.PathUtilRt
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import com.jetbrains.python.psi.LanguageLevel
import org.jetbrains.annotations.ApiStatus
import java.net.URL
import java.nio.charset.StandardCharsets


private val LOG: Logger = logger<Sdks>()


/**
 * Currently only CPython is supported
 */
@ApiStatus.Internal
enum class Product(val title: String) {
  CPython("Python"),
  Miniconda("Miniconda"),
  Anaconda("Anaconda");
}

/**
 * Resource Type enum with autodetection via file extensions.
 */
@ApiStatus.Internal
enum class ResourceType(vararg val extensions: String) {
  MICROSOFT_WINDOWS_EXECUTABLE("exe"),
  MICROSOFT_SOFTWARE_INSTALLER("msi"),
  APPLE_SOFTWARE_PACKAGE("pkg"),
  SHELL_SCRIPT("sh"),
  COMPRESSED("zip", "xz", "tgz", "bz2");

  companion object {
    fun ofFileName(fileName: String): ResourceType {
      val extension = PathUtilRt.getFileExtension(fileName)?.lowercase()
      return entries.first { extension in it.extensions }
    }
  }
}

/**
 * Url-specified file resource. FileName and ResourceType values are calculated by the Url provided (might be declared explicitly).
 * Downloaded size / sha256 should be verified to prevent consistency leaks.
 */

@ApiStatus.Internal
data class Resource(
  val url: Url,
  val size: Long,
  val sha256: String,
  val fileName: String = PathUtilRt.getFileName(url.getPath()),
  val type: ResourceType = ResourceType.ofFileName(fileName),
)


/**
 * Custom prepared installation packages per OS and ArchType.
 * Could contain multiple resources (in case of MSI for example)
 */
@ApiStatus.Internal
data class Binary(
  val os: OS,
  val cpuArch: CpuArch?,
  val resources: List<Resource>,
  val tags: List<String>? = null,
) {
  fun isCompatible(os: OS = OS.CURRENT, cpuArch: CpuArch = CpuArch.CURRENT) = this.os == os && (this.cpuArch?.equals(cpuArch) ?: true)
}


/**
 * Bundle with release version of vendor. Might contain sources or any binary packages.
 * Vendor + Version is a primary key.
 */
@ApiStatus.Internal
data class Release(
  val version: String,
  val product: Product,
  val sources: List<Resource>?,
  val binaries: List<Binary>?,
  val title: String = "${product.title} ${version}",
) : Comparable<Release> {
  override fun compareTo(other: Release) = compareValuesBy(this, other, { it.product }, { it.version })
  override fun toString(): String {
    return title
  }
}


/**
 * Class represents /sdks.json structure with all available SDK release mappings.
 * It has only python section currently.
 */
@ApiStatus.Internal
data class Sdks(
  val python: List<Release> = listOf(),
  val conda: List<Release> = listOf(),
)


@ApiStatus.Internal
fun Version?.toLanguageLevel(): LanguageLevel? = this?.let { LanguageLevel.fromPythonVersion("$major.$minor") }


/**
 * This class replaces missed String-arg constructor in Url class for jackson deserialization.
 *
 * @see com.intellij.util.Url
 */
@ApiStatus.Internal
class UrlDeserializer : JsonDeserializer<Url>() {
  override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): Url {
    return Urls.parseEncoded(p!!.valueAsString)!!
  }
}

@ApiStatus.Internal
class UrlSerializer : JsonSerializer<Url>() {
  override fun serialize(value: Url?, gen: JsonGenerator?, serializers: SerializerProvider?) {
    value?.let {
      gen?.writeString(it.toString())
    }
  }
}


@ApiStatus.Internal
object SdksKeeper {
  private val configUrl: URL? = Sdks::class.java.getResource("/sdks.json")

  val sdks: Sdks by lazy {
    deserialize(load())
  }

  fun pythonReleasesByLanguageLevel(): Map<LanguageLevel, List<Release>> {
    return sdks.python.mapNotNull { release ->
      Version.parseVersion(release.version)?.toLanguageLevel()?.let { it to release }
    }.groupBy({ it.first }, { it.second })
  }

  fun condaReleases(vararg products: Product = arrayOf(Product.Miniconda, Product.Anaconda)): List<Release> {
    return sdks.conda.filter { it.product in products }
  }


  private fun deserialize(content: String?): Sdks = try {
    jacksonObjectMapper()
      .registerModule(
        SimpleModule()
          .addDeserializer(Url::class.java, UrlDeserializer())
      )
      .readValue(content, Sdks::class.java)
  }
  catch (ex: Exception) {
    LOG.error("Json syntax error in the $configUrl", ex)
    Sdks()
  }

  fun serialize(sdks: Sdks): String {
    return jacksonObjectMapper()
      .registerModule(
        SimpleModule()
          .addSerializer(Url::class.java, UrlSerializer())
      )
      .setSerializationInclusion(JsonInclude.Include.NON_NULL)
      .writeValueAsString(sdks)
  }

  private fun load() = configUrl?.let { Resources.toString(it, StandardCharsets.UTF_8) }
}

@ApiStatus.Internal
suspend fun Sdk.setAssociationToModule(module: Module) {
  val path = module.basePath
  assert(path != null) { "Module $module has not paths, and can't be associated" }
  setAssociationToPath(path)
}

@ApiStatus.Internal
suspend fun Sdk.setAssociationToPath(path: String?) {
  val data = getOrCreateAdditionalData().also {
    when {
      path != null -> it.associatedModulePath = path
      else -> it.associatedModulePath = null
    }
  }

  val modificator = sdkModificator
  modificator.sdkAdditionalData = data

  writeAction {
    modificator.commitChanges()
  }
}
