package com.intellij.searchEverywhereMl.ranking.features

import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereFileGroupFeatureProvider.Fields.FILE_GROUP
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereFileGroupFeatureProvider.FileGroup.*
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereFileGroupFeatureProvider.FileGroup.Companion.findGroup
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.charset.Charset


@RunWith(JUnit4::class)
internal class SearchEverywhereFileGroupFeatureProviderTest : SearchEverywhereBaseFileFeaturesProviderTest<SearchEverywhereFileGroupFeatureProvider>(SearchEverywhereFileGroupFeatureProvider::class.java){
  @Test
  fun `file group gets reported when present`() {
    val file = createTempVirtualFile("index.html", null, "", Charset.defaultCharset()).toPsi()

    checkThatFeature(FILE_GROUP)
      .ofElement(file)
      .exists(true)
  }

  @Test
  fun `file group does not get reported when absent`() {
    val file = createTempVirtualFile("foo.txt", null, "", Charset.defaultCharset()).toPsi()

    checkThatFeature(FILE_GROUP)
      .ofElement(file)
      .exists(false)
  }

  @Test
  fun `app_test_js is identified as main type`() {
    val actual = findGroup("app.test.js")
    val expected = MAIN

    Assert.assertEquals(expected, actual)
  }

  @Test
  fun `readme with any extension is identified as readme type`() {
    val actual = findGroup("README.md")
    val expected = README

    Assert.assertEquals(expected, actual)
  }

  @Test
  fun `dockerfile is identified as build type`() {
    val actual = findGroup("dockerfile")
    val expected = BUILD

    Assert.assertEquals(expected, actual)
  }

  @Test
  fun `build is identified as build type`() {
    val actual = findGroup("build")
    val expected = BUILD

    Assert.assertEquals(expected, actual)
  }

  @Test
  fun `no group exists for foo_txt`() {
    val actual = findGroup("foo.txt")
    val expected = null

    Assert.assertEquals(expected, actual)
  }

  @Test
  fun `no group exists for _gitignore`() {
    val actual = findGroup(".gitignore")
    val expected = null

    Assert.assertEquals(expected, actual)
  }
}