package com.intellij.searchEverywhereMl.ranking.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class SearchEverywhereFeatureNameConverterTest {
  @Test
  fun `snake case name is converted to camelCase`() {
    assertEquals("queryLength", toLegacyFeatureName("query_length"))
  }

  @Test
  fun `non trivial legacy names are taken from lookup table`() {
    assertEquals("pluginType", toLegacyFeatureName("action_plugin_type"))
    assertEquals("isFromLibrary", toLegacyFeatureName("is_in_library"))
    assertEquals("maxUsageSE", toLegacyFeatureName("max_usage_se"))
    assertEquals("minUsageSE", toLegacyFeatureName("min_usage_se"))
  }

  @Test
  fun `aliases are added and existing keys are preserved`() {
    val features = mapOf(
      "query_length" to 7,
      "queryLength" to 3,
      "is_in_library" to true,
    )

    val featuresWithAliases = withLegacyFeatureNameAliases(features)

    assertEquals(7, featuresWithAliases["query_length"])
    assertEquals(3, featuresWithAliases["queryLength"])
    assertEquals(true, featuresWithAliases["is_in_library"])
    assertEquals(true, featuresWithAliases["isFromLibrary"])
  }
}
