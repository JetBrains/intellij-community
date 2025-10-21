// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.highlighting

import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase
import org.jetbrains.yaml.YAMLBundle
import org.jetbrains.yaml.inspections.YAMLIncompatibleTypesInspection
import java.util.function.Predicate

class YAMLIncompatibleTypesInspectionTest : BasePlatformTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(YAMLIncompatibleTypesInspection::class.java)
  }

  fun testInArrayAndKeyValue() {
    myFixture.configureByText("test.yaml", """
      top1:
        child1: hi
      top2:
        child2: bye8
        child1: 'no'
        child3:
          - abc
          - sde3
          - ye2s
          - <warning descr="The type of value is 'number' while other values use type 'string'">1<caret></warning>
          - '2'
      top4:
        - abc: 1.0
        - abc: 3E12
        - abc: <warning descr="The type of value is 'boolean' while other values use type 'number'">no</warning>
        - abc: 3
    """.trimIndent())
    myFixture.testHighlighting()
    val wrapWithQuotes = myFixture.findSingleIntention(
      YAMLBundle.message("inspections.incompatible.types.quickfix.wrap.all.quotes.message"))
    myFixture.checkPreviewAndLaunchAction(wrapWithQuotes)
    myFixture.checkResult("""
      top1:
        child1: hi
      top2:
        child2: bye8
        child1: 'no'
        child3:
          - 'abc'
          - 'sde3'
          - 'ye2s'
          - '1'
          - '2'
      top4:
        - abc: 1.0
        - abc: 3E12
        - abc: no
        - abc: 3
    """.trimIndent())
  }

  fun testInDeeplyNested() {
    myFixture.configureByText("test.yaml", """
      top1:
        top2:
          - abc: 1
            inner:
              inner2: "value"
          - abc: 3
            inner:
              inner2: "value"
          - abc: <warning descr="The type of value is 'boolean' while other values use type 'number'">no</warning>
            inner:
              inner2: "value"
          - abc: 3
            inner:
              inner2: <warning descr="The type of value is 'boolean' while other values use type 'string'">fa<caret>lse</warning>
    """.trimIndent())
    myFixture.testHighlighting()
    val availableIntentions = myFixture.availableIntentions
    UsefulTestCase.assertDoesntContain(
      YAMLBundle.message("inspections.incompatible.types.quickfix.wrap.all.quotes.message"),
      availableIntentions.map { it.text },
    )
    val wrapWithQuotes = availableIntentions.first {
      it.text == YAMLBundle.message("inspections.incompatible.types.quickfix.wrap.quotes.message")
    }
    myFixture.checkPreviewAndLaunchAction(wrapWithQuotes)
    myFixture.checkResult("""
      top1:
        top2:
          - abc: 1
            inner:
              inner2: "value"
          - abc: 3
            inner:
              inner2: "value"
          - abc: no
            inner:
              inner2: "value"
          - abc: 3
            inner:
              inner2: "false"
    """.trimIndent())
  }

  fun testDontWarnIfSchemaType() {
    JsonSchemaHighlightingTestBase.registerJsonSchema(myFixture, """
   {
     "type": "object",
     "properties": {
       "prop": {
         "type": "array",
         "items": {
           "type": "number",
           "minimum": 18
         }
       }
     }
   }
    """.trimIndent(), ".json", Predicate { it.name == "test.yaml" })
    myFixture.configureByText("test.yaml", """
      prop:
        - abc
        - cde
        - fgh
        - 1
    """.trimIndent())
    myFixture.testHighlighting()
  }

  fun testStrMacro() {
    myFixture.configureByText("test.yaml", """
      - items:
          - key:   'string'
            value: "string"
          - key:   "string"
            value: string stirng
          - key:   also a string
            value: 'yet another'
          - key:   <warning descr="The type of value is 'number' while other values use type 'string'">1</warning>
            value: !!str true
    """.trimIndent())
    myFixture.testHighlighting()
  }

  fun testTypeByTag() {
    myFixture.configureByText("test.yaml", """
      - items:
          - key:   'string'
            value: !!int 1
          - key:   "string"
            value: !!float 2
          - key:   also a string
            value: !!int 3
          - key:   <warning descr="The type of value is 'number' while other values use type 'string'">1</warning>
            value: <warning descr="The type of value is 'string' while other values use type 'number'">!!str 1</warning>
    """.trimIndent())
    myFixture.testHighlighting()
  }

  fun testWrapWithDoubleQuotes() {
    myFixture.configureByText("test.yaml", """
      prop:
        - abc
        - cde
        - fgh
        - 1<caret>
    """.trimIndent())
    myFixture.checkPreviewAndLaunchAction(
      myFixture.findSingleIntention(YAMLBundle.message("inspections.incompatible.types.quickfix.wrap.all.quotes.message")))
    myFixture.checkResult("""
      prop:
        - "abc"
        - "cde"
        - "fgh"
        - "1"
    """.trimIndent())
  }

  fun testVeryLargeArrayPerformance() {
    myFixture.configureByText("test.yaml", """
      top4:
      ${
      (1..1000).map { i ->
        "  - abc: value$i"
      }.joinToString("\n      ")
    }
        - abc: <warning descr="The type of value is 'boolean' while other values use type 'string'">true</warning>
    """.trimIndent())
    Benchmark.newBenchmark("Inspection should finish in sane time") {
      myFixture.testHighlighting()
    }.warmupIterations(0).attempts(1).start()
  }
}