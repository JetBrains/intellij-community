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

import com.intellij.testFramework.assertInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.packagesearch.api.PackageSearch
import org.jetbrains.idea.packagesearch.api.PackageSearchProvider
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals

class PackageSearchProviderTest {
  private fun <T : Any> T.getResourceText(path: String) = this::class.java.classLoader
    .getResourceAsStream(path)!!.bufferedReader().readText()

  data class EngineInspector(val flow: Flow<Parameters>, val engine: MockEngine)

  private fun getMockEngine(): EngineInspector {
    val channel = Channel<Parameters>(Channel.UNLIMITED)
    val engine = MockEngine { request ->
      channel.send(request.url.parameters)
      respond(
        content = getResourceText("pkgs-response.json"),
        headers = headersOf(
          HttpHeaders.ContentType to listOf(ContentType.Application.PackageSearch.StandardV2.toString())
        )
      )
    }
    return EngineInspector(channel.consumeAsFlow(), engine)
  }

  private fun getClient(engine: MockEngine) = PackageSearchProvider(
    scope = GlobalScope,
    config = TestPackageSearchServiceConfig,
    engine = engine
  )

  @Test
  fun `test suggested packages search`() = runTest {
    val (paramsFlow, engine) = getMockEngine()
    val data = getClient(engine).suggestPrefix(
      groupId = "org.apache.maven",
      artifactId = "maven-plugin-api",
    )
    val params = paramsFlow.first()
    assertEquals("org.apache.maven", params["groupid"])
    assertEquals("maven-plugin-api", params["artifactid"])

    assertEquals(1, data.size)
    val info = assertInstanceOf<MavenRepositoryArtifactInfo>(data.first())
    assertEquals("org.apache.maven", info.groupId)
    assertEquals("maven-plugin-api", info.artifactId)
  }

  @Test
  fun `test packages fulltext search`() = runTest {
    val (paramsFlow, engine) = getMockEngine()
    val data = getClient(engine).fulltextSearch(
      searchString = "maven-plugin-api"
    )

    val params = paramsFlow.first()

    assertEquals("maven-plugin-api", params["query"])

    assertEquals(1, data.size)
    val info = assertInstanceOf<MavenRepositoryArtifactInfo>(data.first())
    assertEquals("org.apache.maven", info.groupId)
    assertEquals("maven-plugin-api", info.artifactId)
  }

}
