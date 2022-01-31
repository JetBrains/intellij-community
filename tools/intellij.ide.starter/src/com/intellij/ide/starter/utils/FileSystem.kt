package com.intellij.ide.starter.utils

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.exec.ExecOutputRedirect
import com.intellij.ide.starter.exec.exec
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.system.SystemInfo
import org.apache.commons.io.FileUtils
import org.kodein.di.instance
import org.rauschig.jarchivelib.ArchiveFormat
import org.rauschig.jarchivelib.ArchiverFactory
import org.rauschig.jarchivelib.CompressionType
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipFile
import kotlin.io.path.*
import kotlin.time.Duration

object FileSystem {
  fun String.cleanPathFromSlashes(replaceWith: String = ""): String = this
    .replace("\"", replaceWith)
    .replace("/", replaceWith)

  fun validatePath(path: Path, additionalString: String = "") {
    if (SystemInfo.isWindows) {
      val pathToValidate = when (additionalString.isNotEmpty()) {
        true -> path.resolve(additionalString).toString()
        false -> path.toString()
      }
      check(pathToValidate.length < 260) {
        "$pathToValidate >= 260 symbols on Windows may lead to unexpected problems"
      }
    }
  }

  fun countFiles(path: Path) = Files.walk(path).use { it.count() }

  fun compressToZip(sourceToCompress: Path, outputArchive: Path) {
    if (sourceToCompress.extension == "zip") {
      logOutput("Looks like $sourceToCompress already compressed to zip file")
      return
    }

    if (outputArchive.exists())
      outputArchive.toFile().deleteRecursively()

    val outputArchiveParentDir = outputArchive.parent.apply { createDirectories() }

    val archiver = ArchiverFactory.createArchiver(ArchiveFormat.ZIP)
    archiver.create(outputArchive.nameWithoutExtension, outputArchiveParentDir.toFile(), sourceToCompress.toFile())
  }

  fun unpackZip(zipFile: Path, targetDir: Path, map: (name: String) -> String? = { it }) {
    try {
      targetDir.createDirectories()

      ZipFile(zipFile.toFile()).use { zip ->
        for (entry in zip.entries()) {
          if (entry.isDirectory) continue
          val file = targetDir.resolve((map(entry.name) ?: continue))
          file.parent.createDirectories()
          file.outputStream().use { zip.getInputStream(entry).use { entryStream -> entryStream.copyTo(it) } }
          file.toFile().setLastModified(entry.lastModifiedTime.toMillis())
        }
      }
    }
    catch (e: Throwable) {
      targetDir.toFile().deleteRecursively()
      zipFile.deleteIfExists()
      throw IOException("Failed to unpack $zipFile to $targetDir. ${e.message}", e)
    }
  }

  fun unpackIfMissing(archive: Path, targetDir: Path) {
    if (Files.isDirectory(targetDir) && Files.newDirectoryStream(targetDir).use { it.iterator().hasNext() }) {
      return
    }

    unpack(archive, targetDir)
  }

  fun unpack(archive: Path, targetDir: Path) {
    logOutput("Extracting $archive to $targetDir")
    //project archive may be empty
    Files.createDirectories(targetDir)
    val name = archive.fileName.toString()
    try {
      when {
        name.endsWith(".zip") || name.endsWith(".ijx") -> unpackZip(archive, targetDir)
        name.endsWith(".tar.gz") -> unpackTarGz(archive, targetDir)
        else -> error("Archive $name is not supported")
      }
    }
    catch (e: IOException) {
      if (e.message?.contains("No space left on device") == true) {
        val paths by di.instance<GlobalPaths>()
        paths.getDiskUsageDiagnostics()
        throw IOException(buildString {
          appendLine("No space left while extracting $archive to $targetDir")
          appendLine(Files.getFileStore(targetDir).getDiskInfo())
          appendLine(paths.getDiskUsageDiagnostics())
        })
      }
      throw e
    }
  }

  fun unpackTarGz(tarFile: File, targetDir: File) {
    targetDir.deleteRecursively()
    unpackTarGz(tarFile.toPath(), targetDir.toPath())
  }

  fun unpackTarGz(tarFile: Path, targetDir: Path) {
    require(tarFile.fileName.toString().endsWith(".tar.gz")) { "File $tarFile must be tar.gz archive" }

    try {
      if (SystemInfo.isWindows) {
        val archiver = ArchiverFactory.createArchiver(ArchiveFormat.TAR, CompressionType.GZIP)
        archiver.extract(tarFile.toFile(), targetDir.toFile())
      }
      else if (SystemInfo.isLinux || SystemInfo.isMac) {
        Files.createDirectories(targetDir)
        exec(
          presentablePurpose = "extract-tar",
          workDir = targetDir,
          timeout = Duration.minutes(10),
          stderrRedirect = ExecOutputRedirect.ToStdOut("tar"),
          args = listOf("tar", "-z", "-x", "-f", tarFile.toAbsolutePath().toString(), "-C", targetDir.toAbsolutePath().toString())
        )
      }
    }
    catch (e: Exception) {
      targetDir.toFile().deleteRecursively()
      tarFile.deleteIfExists()
      throw Exception("Failed to unpack $tarFile. ${e.message}. File and unpack targets are removed.", e)
    }
  }

  fun Path.zippedLength(): Long {
    if (!isRegularFile()) {
      return 0
    }

    val output = object : OutputStream() {
      var count = 0L
      override fun write(b: Int) {
        count++
      }

      override fun write(b: ByteArray) {
        count += b.size
      }

      override fun write(b: ByteArray, off: Int, len: Int) {
        count += len
      }
    }

    GZIPOutputStream(output).use { zipStream ->
      this.inputStream().use { it.copyTo(zipStream, 1024 * 1024) }
    }

    return output.count
  }

  fun Path.getFileOrDirectoryPresentableSize(): String = FileUtils.byteCountToDisplaySize(FileUtils.sizeOf(this.toFile()))

  fun Path.getDirectoryTreePresentableSizes(depth: Int = 1): String {
    val thisPath = this
    return buildString {
      Files.walk(thisPath, depth).use { dirStream ->
        dirStream.forEach { child ->
          if (child == thisPath) {
            appendLine("Total size: ${thisPath.getFileOrDirectoryPresentableSize()}")
          }
          else {
            val indent = "  ".repeat(thisPath.relativize(child).nameCount)
            appendLine("$indent${thisPath.relativize(child)}: " + child.getFileOrDirectoryPresentableSize())
          }
        }
      }
    }
  }

  fun Path.isFileUpToDate(): Boolean {
    if (!this.isRegularFile()) {
      logOutput("File $this does not exist")
      return false
    }
    if (this.fileSize() <= 0) {
      logOutput("File $this is empty")
      return false
    }
    else {
      return this.isUpToDate()
    }
  }

  fun Path.isDirUpToDate(): Boolean {
    if (!this.isDirectory()) {
      logOutput("Path $this does not exist")
      return false
    }

    if (this.fileSize() <= 0) {
      logOutput("Project dir $this is empty")
      return false
    }
    else {
      return this.isUpToDate()
    }
  }

  fun Path.isUpToDate(): Boolean {
    val lastModified = this.toFile().lastModified()
    val currentTime = System.currentTimeMillis()
    val upToDate = currentTime - lastModified < 24 * 60 * 60 * 1000
    if (upToDate) {
      logOutput("$this is up to date")
    }
    else {
      logOutput("$this is not up to date")
    }
    return upToDate
  }
}