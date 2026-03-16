@file:Suppress("TestFunctionName")

package com.intellij.mcpserver

import com.intellij.mcpserver.settings.McpToolFilterOptimizer
import com.intellij.mcpserver.settings.McpToolFilterOptimizer.CategoryToolsInfo
import org.junit.jupiter.api.Test
import org.junit.platform.commons.annotation.Testable
import kotlin.test.assertEquals

@Testable
class McpToolFilterOptimizerTest {

  private fun category(name: String) = McpToolCategory(name, name)

  @Test
  fun `all tools enabled returns default filter`() {
    val cat1 = category("Cat1")
    val categoriesInfo = listOf(
      CategoryToolsInfo(cat1, setOf("Cat1.tool1", "Cat1.tool2"))
    )
    
    val result = McpToolFilterOptimizer.buildOptimizedFilter(
      enabledTools = setOf("Cat1.tool1", "Cat1.tool2"),
      disabledTools = emptySet(),
      categoriesInfo = categoriesInfo
    )
    
    assertEquals("*", result)
  }

  @Test
  fun `all tools disabled returns deny all`() {
    val cat1 = category("Cat1")
    val categoriesInfo = listOf(
      CategoryToolsInfo(cat1, setOf("Cat1.tool1", "Cat1.tool2"))
    )
    
    val result = McpToolFilterOptimizer.buildOptimizedFilter(
      enabledTools = emptySet(),
      disabledTools = setOf("Cat1.tool1", "Cat1.tool2"),
      categoriesInfo = categoriesInfo
    )
    
    assertEquals("-*", result)
  }

  @Test
  fun `all tools in category disabled uses category mask`() {
    val cat1 = category("Cat1")
    val cat2 = category("Cat2")
    val categoriesInfo = listOf(
      CategoryToolsInfo(cat1, setOf("Cat1.tool1", "Cat1.tool2")),
      CategoryToolsInfo(cat2, setOf("Cat2.tool1", "Cat2.tool2"))
    )
    
    // Cat1 all enabled, Cat2 all disabled
    // Majority (50%) of categories have majority enabled, so start with allow all
    val result = McpToolFilterOptimizer.buildOptimizedFilter(
      enabledTools = setOf("Cat1.tool1", "Cat1.tool2"),
      disabledTools = setOf("Cat2.tool1", "Cat2.tool2"),
      categoriesInfo = categoriesInfo
    )
    
    assertEquals("-Cat2.*", result)
  }

  @Test
  fun `all tools in category enabled uses category mask when starting with deny all`() {
    val cat1 = category("Cat1")
    val cat2 = category("Cat2")
    val cat3 = category("Cat3")
    val categoriesInfo = listOf(
      CategoryToolsInfo(cat1, setOf("Cat1.tool1", "Cat1.tool2")),
      CategoryToolsInfo(cat2, setOf("Cat2.tool1", "Cat2.tool2")),
      CategoryToolsInfo(cat3, setOf("Cat3.tool1", "Cat3.tool2"))
    )
    
    // Cat1 all enabled, Cat2 and Cat3 all disabled
    // Less than 50% of categories have majority enabled (1/3 ≈ 33%), so start with deny all
    val result = McpToolFilterOptimizer.buildOptimizedFilter(
      enabledTools = setOf("Cat1.tool1", "Cat1.tool2"),
      disabledTools = setOf("Cat2.tool1", "Cat2.tool2", "Cat3.tool1", "Cat3.tool2"),
      categoriesInfo = categoriesInfo
    )
    
    assertEquals("-*,+Cat1.*", result)
  }

  @Test
  fun `category with less than 50 percent enabled disables category and enables individually`() {
    val cat1 = category("Cat1")
    val cat2 = category("Cat2")
    val categoriesInfo = listOf(
      CategoryToolsInfo(cat1, setOf("Cat1.tool1", "Cat1.tool2", "Cat1.tool3", "Cat1.tool4")),
      CategoryToolsInfo(cat2, setOf("Cat2.tool1", "Cat2.tool2"))
    )
    
    // Cat1: 1 of 4 enabled (25%), Cat2: all enabled
    // 50% of categories have majority enabled, so don't start with deny all
    val result = McpToolFilterOptimizer.buildOptimizedFilter(
      enabledTools = setOf("Cat1.tool1", "Cat2.tool1", "Cat2.tool2"),
      disabledTools = setOf("Cat1.tool2", "Cat1.tool3", "Cat1.tool4"),
      categoriesInfo = categoriesInfo
    )
    
    // Cat1 has <50% enabled, so disable category and enable individually
    assertEquals("-Cat1.*,+Cat1.tool1", result)
  }

  @Test
  fun `category with 50 percent or more enabled disables tools individually`() {
    val cat1 = category("Cat1")
    val cat2 = category("Cat2")
    val categoriesInfo = listOf(
      CategoryToolsInfo(cat1, setOf("Cat1.tool1", "Cat1.tool2", "Cat1.tool3", "Cat1.tool4")),
      CategoryToolsInfo(cat2, setOf("Cat2.tool1", "Cat2.tool2"))
    )
    
    // Cat1: 2 of 4 enabled (50%), Cat2: all enabled
    // Both categories have majority enabled, so don't start with deny all
    val result = McpToolFilterOptimizer.buildOptimizedFilter(
      enabledTools = setOf("Cat1.tool1", "Cat1.tool2", "Cat2.tool1", "Cat2.tool2"),
      disabledTools = setOf("Cat1.tool3", "Cat1.tool4"),
      categoriesInfo = categoriesInfo
    )
    
    // Cat1 has >=50% enabled, so disable tools individually
    assertEquals("-Cat1.tool3,-Cat1.tool4", result)
  }

  @Test
  fun `less than 50 percent categories with majority enabled starts with deny all`() {
    val cat1 = category("Cat1")
    val cat2 = category("Cat2")
    val cat3 = category("Cat3")
    val cat4 = category("Cat4")
    val categoriesInfo = listOf(
      CategoryToolsInfo(cat1, setOf("Cat1.tool1", "Cat1.tool2")),
      CategoryToolsInfo(cat2, setOf("Cat2.tool1", "Cat2.tool2")),
      CategoryToolsInfo(cat3, setOf("Cat3.tool1", "Cat3.tool2")),
      CategoryToolsInfo(cat4, setOf("Cat4.tool1", "Cat4.tool2"))
    )
    
    // Cat1: all enabled, Cat2, Cat3, Cat4: all disabled
    // 1/4 = 25% categories have majority enabled, so start with deny all
    val result = McpToolFilterOptimizer.buildOptimizedFilter(
      enabledTools = setOf("Cat1.tool1", "Cat1.tool2"),
      disabledTools = setOf("Cat2.tool1", "Cat2.tool2", "Cat3.tool1", "Cat3.tool2", "Cat4.tool1", "Cat4.tool2"),
      categoriesInfo = categoriesInfo
    )
    
    assertEquals("-*,+Cat1.*", result)
  }

  @Test
  fun `50 percent or more categories with majority enabled does not start with deny all`() {
    val cat1 = category("Cat1")
    val cat2 = category("Cat2")
    val categoriesInfo = listOf(
      CategoryToolsInfo(cat1, setOf("Cat1.tool1", "Cat1.tool2")),
      CategoryToolsInfo(cat2, setOf("Cat2.tool1", "Cat2.tool2"))
    )
    
    // Cat1: all enabled, Cat2: all disabled
    // 1/2 = 50% categories have majority enabled, so don't start with deny all
    val result = McpToolFilterOptimizer.buildOptimizedFilter(
      enabledTools = setOf("Cat1.tool1", "Cat1.tool2"),
      disabledTools = setOf("Cat2.tool1", "Cat2.tool2"),
      categoriesInfo = categoriesInfo
    )
    
    assertEquals("-Cat2.*", result)
  }

  @Test
  fun `complex scenario with mixed categories`() {
    val cat1 = category("Cat1")
    val cat2 = category("Cat2")
    val cat3 = category("Cat3")
    val categoriesInfo = listOf(
      CategoryToolsInfo(cat1, setOf("Cat1.tool1", "Cat1.tool2", "Cat1.tool3", "Cat1.tool4")),
      CategoryToolsInfo(cat2, setOf("Cat2.tool1", "Cat2.tool2")),
      CategoryToolsInfo(cat3, setOf("Cat3.tool1", "Cat3.tool2", "Cat3.tool3", "Cat3.tool4"))
    )
    
    // Cat1: 3 of 4 enabled (75%), Cat2: all disabled, Cat3: 1 of 4 enabled (25%)
    // 1/3 ≈ 33% categories have majority enabled, so start with deny all
    val result = McpToolFilterOptimizer.buildOptimizedFilter(
      enabledTools = setOf("Cat1.tool1", "Cat1.tool2", "Cat1.tool3", "Cat3.tool1"),
      disabledTools = setOf("Cat1.tool4", "Cat2.tool1", "Cat2.tool2", "Cat3.tool2", "Cat3.tool3", "Cat3.tool4"),
      categoriesInfo = categoriesInfo
    )
    
    // Start with -*, then:
    // Cat1: 75% enabled (>=50%), so enable category and disable individuals
    // Cat2: all disabled, skip (already disabled by -*)
    // Cat3: 25% enabled (<50%), so enable tools individually
    assertEquals("-*,+Cat1.*,-Cat1.tool4,+Cat3.tool1", result)
  }

  @Test
  fun `tools are sorted alphabetically in filter output`() {
    val cat1 = category("Cat1")
    val categoriesInfo = listOf(
      CategoryToolsInfo(cat1, setOf("Cat1.zebra", "Cat1.apple", "Cat1.mango", "Cat1.banana"))
    )
    
    // 2 of 4 enabled (50%), so disable individually
    val result = McpToolFilterOptimizer.buildOptimizedFilter(
      enabledTools = setOf("Cat1.zebra", "Cat1.apple"),
      disabledTools = setOf("Cat1.mango", "Cat1.banana"),
      categoriesInfo = categoriesInfo
    )
    
    // Disabled tools should be sorted
    assertEquals("-Cat1.banana,-Cat1.mango", result)
  }
}
