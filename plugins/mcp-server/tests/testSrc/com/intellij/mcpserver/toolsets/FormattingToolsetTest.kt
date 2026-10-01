@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.mcpserver.GeneralMcpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.FormattingToolset
import com.intellij.mcpserver.util.awaitExternalChangesAndIndexing
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.editorconfig.Utils
import org.editorconfig.configmanagement.extended.EditorConfigCodeStyleSettingsModifier
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.createParentDirectories
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
  fun reformat_file_keeps_external_disk_edit(): Unit = runBlocking(Dispatchers.Default) {
    val targetPath = project.projectDirectory.resolve("src/StaleTarget.java")
    targetPath.writeText("public class StaleTarget {\nint a;\n}\n")
    val targetVirtualFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(targetPath)
                            ?: error("Cannot refresh $targetPath")
    val cachedDocument = readAction { FileDocumentManager.getInstance().getDocument(targetVirtualFile) }
                         ?: error("Cannot load document for $targetPath")

    targetPath.writeText("public class StaleTarget {\nint a;\nvoid externalEditMarker() {\n}\n}\n")

    testMcpTool(FormattingToolset::reformat_file.name, filesInput("src/StaleTarget.java")) { result ->
      // The diff starts from the agent's edit, so it never shows that edit as an added line.
      assertThat(result.textContent.text)
        .contains("-void externalEditMarker() {", "+    void externalEditMarker() {")
    }

    assertThat(targetPath.readText().trimEnd()).isEqualTo(
      """
      public class StaleTarget {
          int a;

          void externalEditMarker() {
          }
      }
      """.trimIndent()
    )
    assertThat(cachedDocument.text).isEqualTo(targetPath.readText())
  }

  @Test
  fun reformat_file_returns_diff_of_formatter_changes(): Unit = runBlocking(Dispatchers.Default) {
    writeProjectFile("src/DiffTarget.java", "public class DiffTarget {\nint a;\n}\n")

    testMcpTool(FormattingToolset::reformat_file.name, filesInput("src/DiffTarget.java")) { result ->
      assertThat(result.textContent.text).isEqualTo(
        """
        --- a/src/DiffTarget.java
        +++ b/src/DiffTarget.java
        @@ -1,3 +1,3 @@
         public class DiffTarget {
        -int a;
        +    int a;
         }
        """.trimIndent()
      )
    }
  }

  @Test
  fun reformat_file_returns_ok_when_nothing_changes(): Unit = runBlocking(Dispatchers.Default) {
    writeProjectFile("src/FormattedTarget.java", "public class FormattedTarget {\n    int a;\n}\n")

    testMcpTool(FormattingToolset::reformat_file.name, filesInput("src/FormattedTarget.java"), "ok")
  }

  @Test
  fun optimize_imports_removes_unused_import(): Unit = runBlocking(Dispatchers.Default) {
    writeProjectFile("src/one/Helper.java", "package one;\n\npublic class Helper {\n}\n")
    writeProjectFile("src/UnusedImport.java", "import one.Helper;\n\npublic class UnusedImport {\n}\n")

    testMcpTool(FormattingToolset::optimize_imports.name, filesInput("src/UnusedImport.java")) { result ->
      assertThat(result.textContent.text).contains("-import one.Helper;")
    }
    assertThat(project.projectDirectory.resolve("src/UnusedImport.java").readText()).doesNotContain("import")
  }

  @Test
  fun optimize_imports_returns_ok_when_nothing_changes(): Unit = runBlocking(Dispatchers.Default) {
    writeProjectFile("src/one/Helper.java", "package one;\n\npublic class Helper {\n}\n")
    writeProjectFile("src/UsedImport.java", "import one.Helper;\n\npublic class UsedImport {\n    Helper helper;\n}\n")

    testMcpTool(FormattingToolset::optimize_imports.name, filesInput("src/UsedImport.java"), "ok")
  }

  @Test
  fun optimize_imports_sees_class_created_next_to_file(): Unit = runBlocking(Dispatchers.Default) {
    writeProjectFile("src/one/Helper.java", "package one;\n\npublic class Helper {\n}\n")
    writeProjectFile("src/two/Holder.java", "package two;\n\nimport one.*;\n\npublic class Holder {\n    Helper helper;\n}\n")
    // The agent creates the class past the VFS. It belongs to the package of Holder, so it wins over the
    // on-demand import, and the import becomes unused. A stale VFS turns it into `import one.Helper;` instead.
    project.projectDirectory.resolve("src/two/Helper.java").writeText("package two;\n\npublic class Helper {\n}\n")

    testMcpTool(FormattingToolset::optimize_imports.name, filesInput("src/two/Holder.java")) { result ->
      assertThat(result.textContent.text).contains("-import one.*;")
    }
    assertThat(project.projectDirectory.resolve("src/two/Holder.java").readText()).doesNotContain("import")
  }

  @Test
  fun optimize_imports_never_adds_missing_import(): Unit = runBlocking(Dispatchers.Default) {
    writeProjectFile("src/one/Helper.java", "package one;\n\npublic class Helper {\n}\n")
    val missingImport = "public class MissingImport {\n    Helper helper;\n}\n"
    writeProjectFile("src/MissingImport.java", missingImport)
    writeProjectFile("src/SecondMissingImport.java", missingImport.replace("MissingImport", "SecondMissingImport"))
    withAddUnambiguousImportsOnTheFly {
      testMcpTool(FormattingToolset::optimize_imports.name, filesInput("src/MissingImport.java", "src/SecondMissingImport.java"), "ok")
    }
  }

  @Test
  fun optimize_imports_processor_adds_missing_import_by_default(): Unit = runBlocking(Dispatchers.Default) {
    writeProjectFile("src/one/Helper.java", "package one;\n\npublic class Helper {\n}\n")
    val missingImport = "public class MissingImport {\n    Helper helper;\n}\n"
    writeProjectFile("src/MissingImport.java", missingImport)
    writeProjectFile("src/SecondMissingImport.java", missingImport.replace("MissingImport", "SecondMissingImport"))
    val targetPath = project.projectDirectory.resolve("src/MissingImport.java")
    val paths = listOf(targetPath, project.projectDirectory.resolve("src/SecondMissingImport.java"))
    val virtualFiles = paths.map { VirtualFileManager.getInstance().refreshAndFindFileByNioPath(it) ?: error("Cannot refresh $it") }
    // The same wait as in the tool: in dumb mode the processor does nothing, and the new files start indexing.
    awaitExternalChangesAndIndexing(project)

    withAddUnambiguousImportsOnTheFly {
      val psiFiles = readAction { virtualFiles.map { PsiManager.getInstance(project).findFile(it) ?: error("Cannot find PSI for $it") } }
      withContext(Dispatchers.EDT) {
        OptimizeImportsProcessor(project, psiFiles.toTypedArray(), "Optimize Imports", null).run()
      }
    }

    val documentText = readAction { FileDocumentManager.getInstance().getDocument(virtualFiles.first())?.text }
    assertThat(documentText).contains("import one.Helper;")
  }

  @Test
  fun reformat_file_optimizes_imports_on_request(): Unit = runBlocking(Dispatchers.Default) {
    writeProjectFile("src/one/Helper.java", "package one;\n\npublic class Helper {\n}\n")
    writeProjectFile("src/Combined.java", "import one.Helper;\n\npublic class Combined {\nint a;\n}\n")

    testMcpTool(
      FormattingToolset::reformat_file.name,
      buildJsonObject {
        put("files", buildJsonArray { add(JsonPrimitive("src/Combined.java")) })
        put("optimizeImports", JsonPrimitive(true))
      }
    ) { result ->
      assertThat(result.textContent.text).contains("-import one.Helper;", "-int a;", "+    int a;")
    }
  }

  private suspend fun withAddUnambiguousImportsOnTheFly(block: suspend () -> Unit) {
    val settings = CodeInsightSettings.getInstance()
    val oldAddImports = settings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY
    settings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true
    try {
      block()
    }
    finally {
      settings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = oldAddImports
    }
  }

  private fun writeProjectFile(relativePath: String, text: String) {
    val path = project.projectDirectory.resolve(relativePath)
    path.createParentDirectories()
    path.writeText(text)
    VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path)
  }

  private fun filesInput(vararg paths: String) = buildJsonObject {
    put("files", buildJsonArray { paths.forEach { add(JsonPrimitive(it)) } })
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
        val targetVirtualFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(targetPath)
                                ?: error("Cannot refresh $targetPath")
        VirtualFileManager.getInstance().refreshAndFindFileByNioPath(secondTargetPath)
        val targetPsiFile = readAction { PsiManager.getInstance(project).findFile(targetVirtualFile) }
                            ?: error("Cannot find PSI for $targetPath")
        val (editorConfigProperties, editorConfigFiles) = Utils.processEditorConfig(project, targetVirtualFile)
        assertThat(editorConfigFiles).isNotEmpty()
        assertThat(editorConfigProperties).containsEntry("indent_size", "2")
        assertThat(CodeStyle.getIndentOptions(targetPsiFile).INDENT_SIZE).isEqualTo(2)

        testMcpTool(FormattingToolset::reformat_file.name, filesInput("src/ReformatTarget.kt", "src/ReformatSecondTarget.kt")) { result ->
          assertThat(result.textContent.text).contains("+++ b/src/ReformatTarget.kt", "+++ b/src/ReformatSecondTarget.kt")
        }

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
    VirtualFileManager.getInstance().refreshAndFindFileByNioPath(editorConfigPath)
  }

  private fun setEditorConfigEnabledInTests(enabled: Boolean) {
    EditorConfigCodeStyleSettingsModifier.Handler.setEnabledInTests(enabled)
    Utils.isEnabledInTests = enabled
    Utils.setFullIntellijSettingsSupportEnabledInTest(enabled)
    Utils.fireEditorConfigChanged(project)
    CodeStyle.dropTemporarySettings(project)
  }
}
