// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlTest {
  @Test
  fun `parse url`() {
    assertUrl("http://aaa/bbb", "http", "aaa", path = "/bbb", encoded = false)
    assertUrl("http://aaa/bbb", "http", "aaa", path = "/bbb")
    assertUrl("http://aaa/b b", "http", "aaa", path = "/b b", encoded = false)
    assertUrl("http://aaa/b%20b", "http", "aaa", path = "/b b")
    assertUrl("http://aaa.aaa:10000/b b", "http", "aaa.aaa", 10000, path = "/b b", encoded = false)
    assertUrl("svn+ssh://user@aaa.aaa:10000/b b", "svn+ssh", "aaa.aaa", 10000, "user", "/b b", encoded = false)
    assertUrl("svn+ssh://user@aaa.aaa:10000/b%20b", "svn+ssh", "aaa.aaa", 10000, "user", "/b b")

    assertUrl("http://aaa/", "http", "aaa")
    assertUrl("http://aaa/bbb/", "http", "aaa", path = "/bbb")
    assertUrl("http://aaa/././bbb/./.", "http", "aaa", path = "/bbb")
  }

  @Test
  fun `default port`() {
    assertEquals(Url.parse("http://aaa"), Url.parse("http://aaa:80"))
    assertEquals(Url.parse("svn+ssh://aaa"), Url.parse("svn+ssh://aaa:22"))
  }

  @Test
  fun `to string encoding`() {
    assertEquals("http://aaa/b%20b", Url.parse("http://aaa/b%20b").toString())
    assertEquals("http://aaa/b b", Url.parse("http://aaa/b%20b").toDecodedString())
  }

  @Test
  fun `append path`() {
    val url = Url.parse("http://aaa")

    assertUrl(url.appendPath("/b%20b"), "http", "aaa", path = "/b b")
    assertUrl(url.appendPath("/b b", false), "http", "aaa", path = "/b b")
    assertUrl(url.appendPath("b%20b"), "http", "aaa", path = "/b b")
    assertUrl(url.appendPath("b b", false), "http", "aaa", path = "/b b")
    assertUrl(url.appendPath("b b/b b/b b", false), "http", "aaa", path = "/b b/b b/b b")

    val urlWithPath = Url.parse("http://aaa/bbb")
    assertUrl(urlWithPath.appendPath("ccc"), "http", "aaa", path = "/bbb/ccc")
    assertUrl(urlWithPath.appendPath("/ccc"), "http", "aaa", path = "/bbb/ccc")
    assertUrl(urlWithPath.appendPath("."), "http", "aaa", path = "/bbb")
    assertUrl(urlWithPath.appendPath(""), "http", "aaa", path = "/bbb")
  }

  @Test
  fun `change user info`() {
    val url = Url.parse("svn+ssh://aaa")
    val withUserInfo = url.setUserInfo("user")
    val noUserInfo = withUserInfo.setUserInfo(null)

    assertUrl(withUserInfo, "svn+ssh", "aaa", userInfo = "user")
    assertUrl(noUserInfo, "svn+ssh", "aaa")
    assertEquals(url, noUserInfo)
  }

  @Test
  fun `common ancestor`() {
    assertCommonUrl("http://aaa/bbb", "svn://aaa/bbb", null)
    assertCommonUrl("http://aaa/bbb", "http://aaa.aaa/bbb", null)
    assertCommonUrl("http://aaa/bbb", "http://aaa:100/bbb", null)
    assertCommonUrl("http://aaa/bbb", "http://user@aaa/bbb", null)
    assertCommonUrl("http://aaa/bbb", "http://aaa/ccc", "http://aaa")
    assertCommonUrl("http://aaa/bbb", "http://aaa/bbc", "http://aaa")
    assertCommonUrl("http://aaa/bbb", "http://aaa/bbb", "http://aaa/bbb")
    assertCommonUrl("http://aaa/bbb", "http://aaa/bbb/bbb", "http://aaa/bbb")
  }

  @Test
  fun tail() {
    assertTail("aaa", "aaa")
    assertTail("aaa/", "aaa")
    assertTail("aaa/bbb", "bbb")
  }

  @Test
  fun `remove tail`() {
    assertRemoveTail("aaa", "")
    assertRemoveTail("aaa/", "")
    assertRemoveTail("/aaa", "")
    assertRemoveTail("aaa/bbb", "aaa")
  }

  @Test
  fun `append paths as strings`() {
    assertAppend("", "aaa", "aaa")
    assertAppend("/", "aaa", "aaa")
    assertAppend("aaa", "", "aaa")
    assertAppend("aaa", "/", "aaa")
    assertAppend("aaa/", "", "aaa")
    assertAppend("/aaa", "aaa", "/aaa/aaa")
    assertAppend("aaa/", "aaa", "aaa/aaa")
    assertAppend("aaa/", "/aaa", "aaa/aaa")
    assertAppend("aaa/", "/aaa/", "aaa/aaa")
  }

  @Test
  fun `is ancestor relative`() {
    assertAncestorRelative("", "", true, "")
    assertAncestorRelative("", "/", true, "")
    assertAncestorRelative("/", "", false, null)
    assertAncestorRelative("aaa", "aaa", true, "")
    assertAncestorRelative("aaa/", "aaa", false, null)
    assertAncestorRelative("aaa", "aaa/", true, "")
    assertAncestorRelative("aaa", "aaa/aaa", true, "aaa")
    assertAncestorRelative("/aaa", "/aaa/aaa", true, "aaa")
    assertAncestorRelative("aaa", "aaaaa/aaa", false, null)
  }

  private fun assertUrl(value: String,
                        protocol: String = "",
                        host: String = "",
                        port: Int = -1,
                        userInfo: String? = null,
                        path: String = "",
                        encoded: Boolean = true) {
    assertUrl(Url.parse(value, encoded), protocol, host, port, userInfo, path)
  }

  private fun assertUrl(url: Url, protocol: String = "", host: String = "", port: Int = -1, userInfo: String? = null, path: String = "") {
    assertEquals(protocol, url.protocol)
    assertEquals(host, url.host)
    assertEquals(port, url.port)
    assertEquals(userInfo, url.userInfo)
    assertEquals(path, url.path)
  }

  private fun assertCommonUrl(url1: String, url2: String, commonAncestor: String?) {
    val commonAncestorUrl = Url.parse(url1, false).commonAncestorWith(Url.parse(url2, false))

    if (commonAncestor == null) assertNull(commonAncestorUrl) else assertEquals(commonAncestor, commonAncestorUrl?.toDecodedString())
  }

  private fun assertTail(url: String, tail: String) = assertEquals(tail, Url.tail(url))
  private fun assertRemoveTail(url: String, withoutTail: String) = assertEquals(withoutTail, Url.removeTail(url))
  private fun assertAppend(url1: String, url2: String, expected: String) = assertEquals(expected, Url.append(url1, url2))
  private fun assertAncestorRelative(parent: String, child: String, isAncestor: Boolean, relative: String?) {
    assertEquals(isAncestor, Url.isAncestor(parent, child))
    assertEquals(relative, Url.getRelative(parent, child))
  }
}