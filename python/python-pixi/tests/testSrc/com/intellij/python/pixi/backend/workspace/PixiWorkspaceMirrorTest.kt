// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.workspace

import com.intellij.execution.Platform
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetPlatform
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.TargetedCommandLine
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.isFailure
import com.jetbrains.python.isSuccess
import com.jetbrains.python.errorProcessing.PyResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.io.path.deleteExisting

@Subsystems.Interpreters
@Layers.Functional
@TestApplication
internal class PixiWorkspaceMirrorTest {
  @Test
  fun `an edit during upload is not accepted as the uploaded baseline`(@TempDir root: Path): Unit = timeoutRunBlocking {
    root.resolve("pixi.toml").writeText("original")
    val target = FakeTarget(root, mapOf("pixi.toml" to "remote"))
    val mirror = target.mirror()
    mirror.uploadOptions().getOrThrow()
    root.resolve("pixi.toml").writeText("edit during upload")
    mirror.captureMapping(target.request())

    assertThat(mirror.synchronize().isFailure).isTrue()

    assertThat(root.resolve("pixi.toml").readText()).isEqualTo("edit during upload")
  }

  @Test
  fun `an unsaved document blocks target recovery`(@TempDir root: Path): Unit = timeoutRunBlocking {
    val manifest = root.resolve("pixi.toml")
    manifest.writeText("original")
    val target = FakeTarget(root, mapOf("pixi.toml" to "remote"))
    val mirror = target.mirror()
    mirror.captureMapping(target.request())
    val file = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(manifest))
    val documents = FileDocumentManager.getInstance()
    val document = edtWriteAction { checkNotNull(documents.getDocument(file)).also { it.setText("unsaved edit") } }
    try {
      assertThat(mirror.synchronize().isFailure).isTrue()
      assertThat(document.text).isEqualTo("unsaved edit")
      assertThat(manifest.readText()).isEqualTo("original")
    }
    finally {
      edtWriteAction {
        document.setText("original")
        documents.saveDocument(document)
      }
    }
    target.mirror().uploadOptions().getOrThrow()
    assertThat(manifest.readText()).isEqualTo("remote")
  }

  @Test
  fun `recovery preserves a new local manifest`(@TempDir root: Path): Unit = timeoutRunBlocking {
    root.resolve("pixi.toml").writeText("original")
    val target = FakeTarget(root, mapOf("pixi.toml" to "remote", "pyproject.toml" to "remote project"))
    val mirror = target.mirror()
    mirror.captureMapping(target.request())
    root.resolve("pyproject.toml").writeText("new local project")

    assertThat(mirror.synchronize().isFailure).isTrue()

    assertThat(root.resolve("pixi.toml").readText()).isEqualTo("original")
    assertThat(root.resolve("pyproject.toml").readText()).isEqualTo("new local project")
    root.resolve("pyproject.toml").deleteExisting()
    target.mirror().uploadOptions().getOrThrow()
    assertThat(root.resolve("pyproject.toml").readText()).isEqualTo("remote project")
  }

  @Test
  fun `recovery preserves a local deletion`(@TempDir root: Path): Unit = timeoutRunBlocking {
    root.resolve("pixi.toml").writeText("original")
    root.resolve("pixi.lock").writeText("original lock")
    val target = FakeTarget(root, mapOf("pixi.toml" to "remote", "pixi.lock" to "remote lock"))
    val mirror = target.mirror()
    mirror.captureMapping(target.request())
    root.resolve("pixi.lock").deleteExisting()

    assertThat(mirror.synchronize().isFailure).isTrue()

    assertThat(root.resolve("pixi.lock")).doesNotExist()
    assertThat(root.resolve("pixi.toml").readText()).isEqualTo("original")
    root.resolve("pixi.lock").writeText("original lock")
    target.mirror().uploadOptions().getOrThrow()
    assertThat(root.resolve("pixi.lock").readText()).isEqualTo("remote lock")
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml", "pixi.lock"])
  fun `recovery preserves a local edit after a failed download`(editedFile: String, @TempDir root: Path): Unit = timeoutRunBlocking {
    val original = mapOf("pixi.toml" to "original workspace", "pyproject.toml" to "original project", "pixi.lock" to "original lock")
    for ((name, content) in original) root.resolve(name).writeText(content)
    val target = FakeTarget(root, original.mapValues { "remote ${it.value}" })
    val mirror = target.mirror()
    mirror.captureMapping(target.request())
    target.failDownload = "pixi.lock"
    assertThat(mirror.synchronize().isFailure).isTrue()
    root.resolve(editedFile).writeText("saved local edit")
    target.failDownload = null

    assertThat(target.mirror().uploadOptions().isFailure).isTrue()

    for ((name, content) in original) {
      assertThat(root.resolve(name).readText()).isEqualTo(if (name == editedFile) "saved local edit" else content)
    }
    root.resolve(editedFile).writeText(original.getValue(editedFile))
    target.mirror().uploadOptions().getOrThrow()
    for ((name, content) in target.files) assertThat(root.resolve(name).readText()).isEqualTo(content)
  }

  @Test
  fun `a local edit during download blocks every replacement`(@TempDir root: Path): Unit = timeoutRunBlocking {
    root.resolve("pixi.toml").writeText("original")
    root.resolve("pixi.lock").writeText("original lock")
    val target = FakeTarget(root, mapOf("pixi.toml" to "remote", "pixi.lock" to "remote lock"))
    val mirror = target.mirror()
    mirror.captureMapping(target.request())
    target.onDownload = { file -> if (file == "pixi.lock") root.resolve("pixi.toml").writeText("concurrent edit") }

    assertThat(mirror.synchronize().isFailure).isTrue()

    assertThat(root.resolve("pixi.toml").readText()).isEqualTo("concurrent edit")
    assertThat(root.resolve("pixi.lock").readText()).isEqualTo("original lock")
  }

  @Test
  fun `uploads exclude the environment prefix and use each completed manifest change`(@TempDir root: Path): Unit = timeoutRunBlocking {
    root.resolve("pixi.toml").writeText("old")
    root.resolve(".pixi/envs/default").createDirectories().resolve("keep.txt").writeText("local prefix")
    root.resolve(".git").createDirectories()
    root.resolve("src").createDirectories()
    val target = FakeTarget(root, mapOf("pixi.toml" to "first", "pixi.lock" to "lock"))
    val mirror = target.mirror()

    assertThat(mirror.uploadOptions().getOrThrow().uploadBeforeExecution?.relativePaths)
      .containsExactlyInAnyOrder("pixi.toml", "src")
    mirror.captureMapping(target.request())
    mirror.synchronize().getOrThrow()
    assertThat(root.resolve("pixi.toml").readText()).isEqualTo("first")
    assertThat(root.resolve("pixi.lock").readText()).isEqualTo("lock")
    assertThat(root.resolve(".pixi/envs/default/keep.txt").readText()).isEqualTo("local prefix")

    target.files["pixi.toml"] = "second"
    mirror.captureMapping(target.request())
    mirror.synchronize().getOrThrow()
    assertThat(root.resolve("pixi.toml").readText()).isEqualTo("second")
    assertThat(mirror.uploadOptions().getOrThrow().uploadBeforeExecution?.relativePaths)
      .containsExactlyInAnyOrder("pixi.toml", "pixi.lock", "src")
    assertThat(target.downloads).containsExactly("pixi.toml", "pixi.lock", "pixi.toml", "pixi.lock")
  }

  @Test
  fun `a failed download blocks a stale upload until the mirror recovers`(@TempDir root: Path): Unit = timeoutRunBlocking {
    root.resolve("pixi.toml").writeText("old")
    val target = FakeTarget(root, mapOf("pixi.toml" to "updated", "pixi.lock" to "lock"))
    val mirror = target.mirror()
    mirror.captureMapping(target.request())
    target.failDownload = "pixi.lock"

    assertThat(mirror.synchronize().isFailure).isTrue()
    assertThat(root.resolve("pixi.toml").readText()).isEqualTo("old")
    assertThat(target.mirror().uploadOptions().isFailure).isTrue()

    target.failDownload = null
    assertThat(target.mirror().uploadOptions().getOrThrow().uploadBeforeExecution?.relativePaths)
      .containsExactlyInAnyOrder("pixi.toml", "pixi.lock")
    assertThat(root.resolve("pixi.toml").readText()).isEqualTo("updated")
    assertThat(target.shutdowns).isEqualTo(3)
  }

  @Test
  fun `a probe failure blocks uploads without changing the manifest`(@TempDir root: Path): Unit = timeoutRunBlocking {
    root.resolve("pixi.toml").writeText("old")
    val target = FakeTarget(root, mapOf("pixi.toml" to "updated"))
    val mirror = target.mirror()
    mirror.captureMapping(target.request())
    target.failProbe = true

    assertThat(mirror.synchronize().isFailure).isTrue()
    assertThat(target.mirror().uploadOptions().isFailure).isTrue()
    assertThat(target.downloads).isEmpty()
    assertThat(root.resolve("pixi.toml").readText()).isEqualTo("old")

    target.failProbe = false
    target.mirror().uploadOptions().getOrThrow()
    assertThat(root.resolve("pixi.toml").readText()).isEqualTo("updated")
  }

  @Test
  fun `the download uses the target separator and a parent mapping`(@TempDir root: Path): Unit = timeoutRunBlocking {
    val workspace = root.resolve("workspace").createDirectories()
    val target = FakeTarget(workspace, mapOf("pixi.toml" to "target"), Platform.WINDOWS)
    val mirror = target.mirror()
    val request = target.request()
    request.uploadVolumes.clear()
    request.uploadVolumes.add(TargetEnvironment.UploadRoot(root, TargetEnvironment.TargetPath.Persistent("C:\\remote project")))
    mirror.captureMapping(request)

    mirror.synchronize().getOrThrow()

    assertThat(target.remoteRoots).containsExactly("C:\\remote project\\workspace")
    assertThat(mirror.resolvePath(".pixi/envs/docs").getOrThrow())
      .isEqualTo("C:\\remote project\\workspace\\.pixi\\envs\\docs")
    assertThat(workspace.resolve("pixi.toml").readText()).isEqualTo("target")
  }

  @Test
  fun `a parent upload volume cannot copy local environment prefixes`(@TempDir root: Path): Unit = timeoutRunBlocking {
    val workspace = root.resolve("workspace").createDirectories()
    workspace.resolve(".pixi/envs/default").createDirectories().resolve("keep.txt").writeText("local prefix")
    val target = FakeTarget(workspace, emptyMap())
    target.uploadRoot = root

    assertThat(target.mirror().uploadOptions().isFailure).isTrue()
    assertThat(target.downloads).isEmpty()
    assertThat(target.shutdowns).isZero()
    assertThat(workspace.resolve(".pixi/envs/default/keep.txt").readText()).isEqualTo("local prefix")
  }

  @Test
  fun `a temporary mapping cannot accept another upload`(@TempDir root: Path): Unit = timeoutRunBlocking {
    val target = FakeTarget(root, emptyMap())
    val mirror = target.mirror()
    val request = target.request()
    request.uploadVolumes.clear()
    request.uploadVolumes.add(TargetEnvironment.UploadRoot(root, TargetEnvironment.TargetPath.Temporary()))
    mirror.captureMapping(request)

    assertThat(mirror.synchronize().isFailure).isTrue()
    assertThat(mirror.uploadOptions().isFailure).isTrue()
  }

  @Test
  fun `a corrected persistent mapping recovers after a temporary mapping fails`(@TempDir root: Path): Unit = timeoutRunBlocking {
    root.resolve("pixi.toml").writeText("original")
    val target = FakeTarget(root, mapOf("pixi.toml" to "updated"))
    val temporary = target.request().apply {
      uploadVolumes.clear()
      uploadVolumes.add(TargetEnvironment.UploadRoot(root, TargetEnvironment.TargetPath.Temporary()))
    }
    val failed = target.mirror()
    failed.captureMapping(temporary)
    assertThat(failed.synchronize().isFailure).isTrue()
    assertThat(root.resolve("pixi.toml").readText()).isEqualTo("original")

    val recovered = target.mirror()
    assertThat(recovered.uploadOptions().isSuccess).isTrue()
    recovered.captureMapping(target.request())
    recovered.synchronize().getOrThrow()

    assertThat(root.resolve("pixi.toml").readText()).isEqualTo("updated")
  }

  private class FakeTarget(
    private val root: Path,
    initialFiles: Map<String, String>,
    private val platform: Platform = Platform.UNIX,
  ) {
    val files = initialFiles.toMutableMap()
    val downloads = mutableListOf<String>()
    val remoteRoots = mutableListOf<String>()
    var failDownload: String? = null
    var onDownload: (String) -> Unit = {}
    var failProbe = false
    var uploadRoot: Path = root
    var shutdowns = 0
    private val id = root.toString()

    fun mirror(): PixiWorkspaceMirror = PixiWorkspaceMirror(root, id, ::request) { path ->
      if (failProbe) PyResult.localizedError("The test probe failed.")
      else PyResult.success(files.keys.any { path.endsWith(it) })
    }

    fun request(): TargetEnvironmentRequest = object : TargetEnvironmentRequest {
      override val targetPlatform = TargetPlatform(platform)
      override val configuration: TargetEnvironmentConfiguration? = null
      override val uploadVolumes = mutableSetOf(
        TargetEnvironment.UploadRoot(uploadRoot, TargetEnvironment.TargetPath.Persistent("/remote project")),
      )
      override val downloadVolumes = mutableSetOf<TargetEnvironment.DownloadRoot>()
      override var projectPathOnTarget: String = "/remote project"

      override fun onEnvironmentPrepared(callback: (TargetEnvironment, TargetProgressIndicator) -> Unit) = Unit

      override fun prepareEnvironment(progressIndicator: TargetProgressIndicator): TargetEnvironment {
        val request = this
        return object : TargetEnvironment(request) {
          override val targetPlatform = request.targetPlatform
          override val downloadVolumes = request.downloadVolumes.associateWith { downloadRoot ->
            val destination = checkNotNull(downloadRoot.localRootPath)
            val remote = (downloadRoot.targetRootPath as TargetPath.Persistent).absolutePath
            remoteRoots.add(remote)
            object : DownloadableVolume {
              override val localRoot: Path = destination
              override val targetRoot: String = remote
              override fun resolveTargetPath(relativePath: String): String = "$targetRoot/$relativePath"
              override fun download(relativePath: String, progressIndicator: ProgressIndicator) {
                downloads.add(relativePath)
                if (relativePath == failDownload) throw IOException("The test download failed.")
                onDownload(relativePath)
                destination.resolve(relativePath).writeText(checkNotNull(files[relativePath]))
              }
            }
          }

          override fun createProcess(commandLine: TargetedCommandLine, indicator: ProgressIndicator): Process =
            error("No process is needed.")

          override fun shutdown() {
            shutdowns++
          }
        }
      }
    }
  }
}
