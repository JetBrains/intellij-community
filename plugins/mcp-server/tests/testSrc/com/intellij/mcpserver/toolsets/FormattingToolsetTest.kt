@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.application.options.CodeStyle
import com.intellij.mcpserver.GeneralMcpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.FormattingToolset
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.openapi.application.readAction
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.editorconfig.Utils
import org.editorconfig.configmanagement.extended.EditorConfigCodeStyleSettingsModifier
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
        put("files", buildJsonArray {
          add(JsonPrimitive(project.projectDirectory.relativizeIfPossible(mainJavaFile)))
        })
      },
      "ok"
    )
  }

  @Test
  fun reformat_file_uses_editorconfig_indent_for_kotlin() {
    runBlocking(Dispatchers.Default) {
      assumeTrue(isKotlinPluginInstalled(), "Kotlin plugin is required for this test")
      assumeTrue(isEditorConfigPluginInstalled(), "EditorConfig plugin is required for this test")

      writeEditorConfig()
      setEditorConfigEnabledInTests(true)
      try {
        val targetPath = project.projectDirectory.resolve("src/ReformatTarget.kt")
        val secondTargetPath = project.projectDirectory.resolve("src/ReformatSecondTarget.kt")
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
        secondTargetPath.writeText(
          """
          package sample

          class ReformatSecondTarget {
          fun call() {
          println("ok")
          }
          }
          """.trimIndent()
        )
        val targetVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(targetPath)
                                ?: error("Cannot refresh $targetPath")
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(secondTargetPath)
        val targetPsiFile = readAction { PsiManager.getInstance(project).findFile(targetVirtualFile) }
                            ?: error("Cannot find PSI for $targetPath")
        val (editorConfigProperties, editorConfigFiles) = Utils.processEditorConfig(project, targetVirtualFile)
        assertThat(editorConfigFiles).isNotEmpty()
        assertThat(editorConfigProperties).containsEntry("indent_size", "2")
        assertThat(CodeStyle.getIndentOptions(targetPsiFile).INDENT_SIZE).isEqualTo(2)

        testMcpTool(
          FormattingToolset::reformat_file.name,
          buildJsonObject {
            put("files", buildJsonArray {
              add(JsonPrimitive("src/ReformatTarget.kt"))
              add(JsonPrimitive("src/ReformatSecondTarget.kt"))
            })
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
        assertThat(secondTargetPath.readText().trimEnd()).isEqualTo(
          """
          package sample

          class ReformatSecondTarget {
            fun call() {
              println("ok")
            }
          }
          """.trimIndent()
        )
      }
      finally {
        setEditorConfigEnabledInTests(false)
      }
    }
  }

  private fun isKotlinPluginInstalled(): Boolean {
    return PluginManagerCore.isPluginInstalled(PluginId.getId("org.jetbrains.kotlin"))
  }

  private fun isEditorConfigPluginInstalled(): Boolean {
    return PluginManagerCore.isPluginInstalled(PluginId.getId("org.editorconfig.editorconfigjetbrains"))
  }

  private fun writeEditorConfig() {
    // This fixture's EditorConfig lookup stops at the source root, so keep the config next to the files under test.
    val editorConfigPath = project.projectDirectory.resolve("src/.editorconfig")
    editorConfigPath.writeText(
      """
      root = true

      [*]
      indent_style = space
      indent_size = 2
      ij_continuation_indent_size = 2
      tab_width = 2
      """.trimIndent()
    )
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(editorConfigPath)
  }

  private fun setEditorConfigEnabledInTests(enabled: Boolean) {
    EditorConfigCodeStyleSettingsModifier.Handler.setEnabledInTests(enabled)
    Utils.isEnabledInTests = enabled
    Utils.setFullIntellijSettingsSupportEnabledInTest(enabled)
    Utils.fireEditorConfigChanged(project)
    CodeStyle.dropTemporarySettings(project)
  }
}
