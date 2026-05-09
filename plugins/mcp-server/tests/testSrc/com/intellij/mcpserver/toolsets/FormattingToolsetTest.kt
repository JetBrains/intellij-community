@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.mcpserver.GeneralMcpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.FormattingToolset
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.readText
import kotlin.io.path.writeText

class FormattingToolsetTest : GeneralMcpToolsetTestBase() {
  @Test
  fun reformat_file() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      FormattingToolset::reformat_file.name,
      buildJsonObject {
        put("path", JsonPrimitive(project.projectDirectory.relativizeIfPossible(mainJavaFile)))
      },
      "ok"
    )
  }

  @Test
  fun reformat_file_uses_editorconfig_indent_for_kotlin() {
    runBlocking(Dispatchers.Default) {
      assumeTrue(isKotlinPluginInstalled(), "Kotlin plugin is required for this test")
      assumeTrue(isEditorConfigPluginInstalled(), "EditorConfig plugin is required for this test")

      val targetPath = project.projectDirectory.resolve("src/ReformatTarget.kt")
      targetPath.writeText(
        """
        package sample

        class ReformatTarget {
        fun call() {
        println("ok")
        }
        }
        """.trimIndent()
      )
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(targetPath)

      testMcpTool(
        FormattingToolset::reformat_file.name,
        buildJsonObject {
          put("path", JsonPrimitive("src/ReformatTarget.kt"))
        },
        "ok"
      )

      assertThat(targetPath.readText().trimEnd()).isEqualTo(
        """
        package sample

        class ReformatTarget {
          fun call() {
            println("ok")
          }
        }
        """.trimIndent()
      )
    }
  }

  private fun isKotlinPluginInstalled(): Boolean {
    return PluginManagerCore.isPluginInstalled(PluginId.getId("org.jetbrains.kotlin"))
  }

  private fun isEditorConfigPluginInstalled(): Boolean {
    return PluginManagerCore.isPluginInstalled(PluginId.getId("org.editorconfig.editorconfigjetbrains"))
  }
}
