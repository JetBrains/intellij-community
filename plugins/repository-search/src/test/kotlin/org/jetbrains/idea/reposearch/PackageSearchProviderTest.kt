/*
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.idea.reposearch

import com.intellij.openapi.util.Ref
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.packagesearch.PackageSearchServiceConfig
import org.jetbrains.idea.packagesearch.api.PackageSearchApiContentTypes
import org.jetbrains.idea.packagesearch.api.PackageSearchProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.*
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI

class PackageSearchProviderTest {
  companion object {
    private const val LOCALHOST = "127.0.0.1"
    private const val fulltextEndpoint = "/package"
    private const val suggestEndpoint = "/package"
  }

  private lateinit var myServer: HttpServer
  private lateinit var myUrl: String

  private val response: String = this::class.java.classLoader.getResourceAsStream("pkgs-response.json")!!.bufferedReader().readText()

  @Before
  fun setUp() {
    myServer = HttpServer.create().apply {
      bind(InetSocketAddress(LOCALHOST, 0), 1)
      start()
    }
    myUrl = "http://" + LOCALHOST + ":" + myServer.address?.port
  }

  @After
  fun tearDown() {
    myServer.stop(0)
  }

  @Test
  fun `test suggested packages search`() {
    val params = createServer(suggestEndpoint, response)
    val data: MutableList<RepositoryArtifactData> = ArrayList()

    PackageSearchProvider(MyPackageSearchServiceConfig()).suggestPrefix(
      groupId = "org.apache.maven",
      artifactId = "maven-plugin-api",
      consumer = data::add
    )

    assertNotNull(params.get())
    assertEquals("org.apache.maven", params.get()["groupid"])
    assertEquals("maven-plugin-api", params.get()["artifactid"])

    assertEquals(1, data.size)
    val info = data.first()
    assertInstanceOf(MavenRepositoryArtifactInfo::class.java, info)
    info as MavenRepositoryArtifactInfo
    assertEquals("org.apache.maven", info.groupId)
    assertEquals("maven-plugin-api", info.artifactId)
  }

  @Test
  fun `test packages fulltext search`() {
    val params = createServer(fulltextEndpoint, response)
    val data: MutableList<RepositoryArtifactData> = ArrayList()

    PackageSearchProvider(MyPackageSearchServiceConfig()).fulltextSearch(
      searchString = "maven-plugin-api",
      consumer = data::add
    )

    assertNotNull(params.get())
    assertEquals("maven-plugin-api", params.get()["query"])

    assertEquals(1, data.size)
    val info = data.first()
    assertInstanceOf(MavenRepositoryArtifactInfo::class.java, info)
    info as MavenRepositoryArtifactInfo
    assertEquals("org.apache.maven", info.groupId)
    assertEquals("maven-plugin-api", info.artifactId)
  }

  private fun createServer(endpoint: String, serverResponse: String): Ref<Map<String, String>> {
    val params = Ref<Map<String, String>>()
    myServer.createContext(endpoint) { ex: HttpExchange ->
      try {
        params.set(getQueryMap(ex.requestURI))
        ex.responseHeaders.add("Content-Type", "${PackageSearchApiContentTypes.StandardV2}; charset=UTF-8")
        val responseBody = serverResponse.toByteArray()
        ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseBody.size.toLong())
        ex.responseBody.write(responseBody)
      }
      finally {
        ex.close()
      }
    }
    return params
  }

  private fun getQueryMap(uri: URI): Map<String, String>? {
    val params = uri.query.split("&")
    val map = HashMap<String, String>()

    for (param in params) {
      val split = param.split("=")
      map[split[0]] = split[1]
    }

    return map
  }

  private inner class MyPackageSearchServiceConfig : PackageSearchServiceConfig {
    override val baseUrl: String
      get() = myUrl

    override val timeoutInSeconds: Int
      get() = 1000

    override val userAgent: String
      get() = "TEST"

    override val forceHttps: Boolean
      get() = false

    override val headers: List<Pair<String, String>>
      get() = emptyList()
  }
}