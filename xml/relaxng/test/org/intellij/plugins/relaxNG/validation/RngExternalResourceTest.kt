// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.relaxNG.validation

import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.sun.net.httpserver.HttpServer
import org.kohsuke.rngom.digested.DPattern
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Collections

/**
 * Checks that the validation of a RELAX NG schema does not load a resource that is not a local file.
 */
class RngExternalResourceTest : BasePlatformTestCase() {
  private lateinit var server: HttpServer
  private val requests: MutableList<String> = Collections.synchronizedList(ArrayList())

  override fun createTempDirTestFixture(): TempDirTestFixture = TempDirTestFixtureImpl()

  override fun setUp() {
    super.setUp()
    server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    server.createContext("/") { exchange ->
      requests.add(exchange.requestURI.toString())
      val body = """<grammar xmlns="http://relaxng.org/ns/structure/1.0"><start><element name="remote"><empty/></element></start></grammar>"""
        .toByteArray()
      exchange.sendResponseHeaders(200, body.size.toLong())
      exchange.responseBody.use { it.write(body) }
    }
    server.start()
  }

  override fun tearDown() {
    try {
      server.stop(0)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  private val baseUrl: String
    get() = "http://${server.address.hostString}:${server.address.port}"

  fun testRngInclude() {
    doTestNoRequest("schema.rng", """
      <grammar xmlns="http://relaxng.org/ns/structure/1.0">
        <include href="$baseUrl/include.rng"/>
      </grammar>
    """.trimIndent())
  }

  fun testRngExternalRef() {
    doTestNoRequest("schema.rng", """
      <grammar xmlns="http://relaxng.org/ns/structure/1.0">
        <start><externalRef href="$baseUrl/external.rng"/></start>
      </grammar>
    """.trimIndent())
  }

  fun testRngExternalDtd() {
    doTestNoRequest("schema.rng", """
      <!DOCTYPE grammar SYSTEM "$baseUrl/external.dtd">
      <grammar xmlns="http://relaxng.org/ns/structure/1.0">
        <start><element name="a"><empty/></element></start>
      </grammar>
    """.trimIndent())
  }

  fun testRngExternalEntities() {
    val secret = myFixture.tempDirFixture.createFile("secret.txt", "secret").url
    doTestNoRequest("schema.rng", """
      <!DOCTYPE grammar [
        <!ENTITY % secret SYSTEM "$secret">
        <!ENTITY % remote SYSTEM "$baseUrl/remote.dtd">
        %remote;
      ]>
      <grammar xmlns="http://relaxng.org/ns/structure/1.0">
        <start><element name="a"><empty/></element></start>
      </grammar>
    """.trimIndent())
  }

  fun testRncInclude() {
    doTestNoRequest("schema.rnc", """include "$baseUrl/include.rnc"""")
  }

  fun testRncExternal() {
    doTestNoRequest("schema.rnc", """start = external "$baseUrl/external.rnc"""")
  }

  fun testLocalRngIncludeIsLoaded() {
    myFixture.addFileToProject("include.rng", """
      <grammar xmlns="http://relaxng.org/ns/structure/1.0">
        <start><element name="local"><empty/></element></start>
      </grammar>
    """.trimIndent())
    val file = myFixture.configureByText("schema.rng", """
      <grammar xmlns="http://relaxng.org/ns/structure/1.0">
        <include href="include.rng"/>
      </grammar>
    """.trimIndent())

    val pattern: DPattern? = RngParser.parsePattern(file, RngParser.DEFAULT_HANDLER, false)
    assertNotNull(pattern)
    assertNotNull(RngParser.getCachedSchema(file as XmlFile))
  }

  fun testLocalRncIncludeIsLoaded() {
    myFixture.addFileToProject("include.rnc", """start = element local { empty }""")
    val file = myFixture.configureByText("schema.rnc", """include "include.rnc"""")

    val pattern: DPattern? = RngParser.parsePattern(file, RngParser.DEFAULT_HANDLER, false)
    assertNotNull(pattern)
    assertNotNull(RngParser.getCachedSchema(file as XmlFile))
  }

  fun testIsLocalUri() {
    assertTrue(LocalOnlyXmlReaderCreator.isLocalUri("file:/home/user/schema.rng"))
    assertTrue(LocalOnlyXmlReaderCreator.isLocalUri("file:///home/user/schema.rng"))
    assertTrue(LocalOnlyXmlReaderCreator.isLocalUri("file://localhost/home/user/schema.rng"))
    assertTrue(LocalOnlyXmlReaderCreator.isLocalUri("file:/C:/Users/user/my%20schema.rng"))
    assertTrue(LocalOnlyXmlReaderCreator.isLocalUri("jar:file:/home/user/lib.jar!/schema.rng"))

    assertFalse(LocalOnlyXmlReaderCreator.isLocalUri("http://example.com/schema.rng"))
    assertFalse(LocalOnlyXmlReaderCreator.isLocalUri("https://example.com/schema.rng"))
    assertFalse(LocalOnlyXmlReaderCreator.isLocalUri("ftp://example.com/schema.rng"))
    assertFalse(LocalOnlyXmlReaderCreator.isLocalUri("jar:http://example.com/lib.jar!/schema.rng"))
    assertFalse(LocalOnlyXmlReaderCreator.isLocalUri("file://example.com/share/schema.rng"))
    assertFalse(LocalOnlyXmlReaderCreator.isLocalUri("file:////example.com/share/schema.rng"))
    assertFalse(LocalOnlyXmlReaderCreator.isLocalUri("file:/%2Fexample.com/share/schema.rng"))
    assertFalse(LocalOnlyXmlReaderCreator.isLocalUri("file:\\\\example.com\\share\\schema.rng"))
    assertFalse(LocalOnlyXmlReaderCreator.isLocalUri("schema.rng"))
  }

  private fun doTestNoRequest(fileName: String, text: String) {
    val file = myFixture.configureByText(fileName, text)

    RngParser.parsePattern(file, RngParser.DEFAULT_HANDLER, true)
    RngParser.parsePattern(file, RngParser.DEFAULT_HANDLER, false)
    RngParser.getCachedSchema(file as XmlFile)
    myFixture.doHighlighting()

    assertEmpty(requests)
  }
}
