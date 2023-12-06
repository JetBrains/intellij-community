// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.common.io.Resources
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Version
import com.intellij.util.PathUtilRt
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import com.jetbrains.python.psi.LanguageLevel
import java.net.URL
import java.nio.charset.StandardCharsets


private val LOG: Logger = logger<Sdks>()


/**
 * Currently only CPython is supported, PyPy was added to check future structure flexibility.
 */
enum class Product(val title: String) {
  CPython("Python"),
  PyPy("PyPy");
}

/**
 * Resource Type enum with autodetection via file extensions.
 */
enum class ResourceType(vararg val extensions: String) {
  MICROSOFT_WINDOWS_EXECUTABLE("exe"),
  MICROSOFT_SOFTWARE_INSTALLER("msi"),
  APPLE_SOFTWARE_PACKAGE("pkg"),
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
data class Binary(
  val os: OS,
  val cpuArch: CpuArch?,
  val resources: List<Resource>,
) {
  fun isCompatible(os: OS = OS.CURRENT, cpuArch: CpuArch = CpuArch.CURRENT) = this.os == os && (this.cpuArch?.equals(cpuArch) ?: true)
}


/**
 * Bundle with release version of vendor. Might contain sources or any binary packages.
 * Vendor + Version is a primary key.
 */
data class Release(
  val version: Version,
  val product: Product,
  val sources: List<Resource>?,
  val binaries: List<Binary>?,
  val title: String = "${product.title} ${version}"
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
data class Sdks(
  val python: List<Release> = listOf(),
)


fun Version.toLanguageLevel(): LanguageLevel? = LanguageLevel.fromPythonVersion("$major.$minor")


/**
 * This class replaces missed String-arg constructor in Version class for jackson deserialization.
 *
 * @see com.intellij.openapi.util.Version
 */
class VersionDeserializer : JsonDeserializer<Version>() {
  override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): Version {
    return Version.parseVersion(p!!.valueAsString)!!
  }
}
class VersionSerializer : JsonSerializer<Version>() {
  override fun serialize(value: Version?, gen: JsonGenerator?, serializers: SerializerProvider?) {
    value?.let {
      gen?.writeString(it.toString())
    }
  }
}

/**
 * This class replaces missed String-arg constructor in Url class for jackson deserialization.
 *
 * @see com.intellij.util.Url
 */
class UrlDeserializer : JsonDeserializer<Url>() {
  override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): Url {
    return Urls.parseEncoded(p!!.valueAsString)!!
  }
}

class UrlSerializer : JsonSerializer<Url>() {
  override fun serialize(value: Url?, gen: JsonGenerator?, serializers: SerializerProvider?) {
    value?.let {
      gen?.writeString(it.toString())
    }
  }
}

object SdksKeeper {
  private val configUrl: URL? = Sdks::class.java.getResource("/sdks.json")

  val sdks: Sdks by lazy {
    deserialize(load())
  }

  fun pythonReleasesByLanguageLevel(): Map<LanguageLevel, List<Release>> {
    return sdks.python.filter { it.version.toLanguageLevel() != null }.groupBy { it.version.toLanguageLevel()!! }
  }


  private fun deserialize(content: String?): Sdks = try {
    jacksonObjectMapper()
      .registerModule(
        SimpleModule()
          .addDeserializer(Version::class.java, VersionDeserializer())
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
          .addSerializer(Version::class.java, VersionSerializer())
          .addSerializer(Url::class.java, UrlSerializer())
      )
      .setSerializationInclusion(JsonInclude.Include.NON_NULL)
      .writeValueAsString(sdks)
  }

  private fun load() = configUrl?.let { Resources.toString(it, StandardCharsets.UTF_8) }
}
