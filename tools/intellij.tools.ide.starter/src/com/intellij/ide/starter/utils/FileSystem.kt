package com.intellij.ide.starter.utils

import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.ThreeState
import com.intellij.util.io.zip.JBZipEntry
import com.intellij.util.io.zip.JBZipFile
import org.rauschig.jarchivelib.ArchiveFormat
import org.rauschig.jarchivelib.ArchiverFactory
import org.rauschig.jarchivelib.CompressionType
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.FileStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.time.Duration
import java.time.Instant
import java.util.zip.GZIPOutputStream
import kotlin.io.path.*
import kotlin.time.Duration.Companion.minutes

// TODO: https://youtrack.jetbrains.com/issue/AT-3187/Support-archives-unpacking-on-remote-machines-in-com.intellij.ide.starter.utils.FileSystem
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

  fun hasAtLeastFiles(path: Path, minCount: Long): Boolean =
    Files.walk(path).use { stream ->
      val iterator = stream.iterator()
      var seen = 0L
      while (iterator.hasNext()) {
        iterator.next()
        if (++seen >= minCount) return true
      }
      return false
    }

  fun compressToZip(sourceToCompress: Path, outputArchive: Path) {
    if (sourceToCompress.extension == "zip") {
      logOutput("Looks like $sourceToCompress already compressed to zip file")
      return
    }

    if (outputArchive.exists())
      outputArchive.deleteRecursivelyQuietly()

    val outputArchiveParentDir = outputArchive.parent.apply { createDirectories() }

    val archiver = ArchiverFactory.createArchiver(ArchiveFormat.ZIP)
    archiver.create(outputArchive.nameWithoutExtension, outputArchiveParentDir.toFile(), sourceToCompress.toFile())
  }

  fun unpackZip(zipFile: Path, targetDir: Path, map: (name: String) -> String? = { it }) {
    try {
      targetDir.createDirectories()

      // Data class to store symlink information
      data class SymlinkInfo(val file: Path, val targetPath: String)

      val symlinks = mutableListOf<SymlinkInfo>()

      JBZipFile(zipFile, StandardCharsets.UTF_8, false, ThreeState.UNSURE).use { zip ->
        for (entry in zip.entries) {
          if (entry.isDirectory) {
            val dir = targetDir.resolve(entry.name)
            dir.createDirectories()
            continue
          }
          val file = targetDir.resolve((map(entry.name) ?: continue))
          file.parent.createDirectories()

          if (isSymlink(entry)) {
            val targetPath = String(entry.data)
            symlinks.add(SymlinkInfo(file, targetPath))
          }
          else {
            file.outputStream().use {
              entry.inputStream.use { entryStream -> entryStream.copyTo(it) }
            }
            file.setLastModifiedTime(FileTime.fromMillis(entry.time))
            setFilePermissions(file, entry)
          }
        }

        for (symlinkInfo in symlinks) {
          try {
            Files.deleteIfExists(symlinkInfo.file)
            Files.createSymbolicLink(symlinkInfo.file, Path.of(symlinkInfo.targetPath))
          }
          catch (e: Exception) {
            logOutput("Failed to create symbolic link at ${symlinkInfo.file}, falling back to regular file: ${e.message}")
          }
        }
      }
    }
    catch (e: Throwable) {
      targetDir.deleteRecursivelyQuietly()
      zipFile.deleteIfExists()
      throw IOException("Failed to unpack $zipFile to $targetDir. ${e.message}", e)
    }
  }

  private fun setFilePermissions(file: Path, entry: JBZipEntry) {
    try {
      if (!Files.getFileStore(file).supportsFileAttributeView(PosixFileAttributeView::class.java)) return

      val externalAttrs = entry.externalAttributes
      val unixMode = (externalAttrs shr 16).toInt()
      if (externalAttrs == 0L || unixMode == 0) return

      // Convert Unix mode to Java PosixFilePermissions
      val permissions = mutableSetOf<PosixFilePermission>()

      // Owner permissions - ALWAYS add read/write, preserve execute
      permissions.add(PosixFilePermission.OWNER_READ)
      permissions.add(PosixFilePermission.OWNER_WRITE)
      if (unixMode and 0x040 != 0) permissions.add(PosixFilePermission.OWNER_EXECUTE)

      // Group permissions - ALWAYS add read/write, preserve execute
      permissions.add(PosixFilePermission.GROUP_READ)
      permissions.add(PosixFilePermission.GROUP_WRITE)
      if (unixMode and 0x008 != 0) permissions.add(PosixFilePermission.GROUP_EXECUTE)

      // Others permissions - ALWAYS add read/write, preserve execute
      permissions.add(PosixFilePermission.OTHERS_READ)
      permissions.add(PosixFilePermission.OTHERS_WRITE)
      if (unixMode and 0x001 != 0) permissions.add(PosixFilePermission.OTHERS_EXECUTE)

      Files.setPosixFilePermissions(file, permissions)
    }
    catch (e: Exception) {
      logOutput("Failed to set permissions for ${file}: ${e.message}")
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
        name.endsWith(".zip") ||
        name.endsWith(".ijx") ||
        name.endsWith(".jar") -> unpackZip(archive, targetDir)

        name.endsWith(".tar.gz") -> unpackTarGz(archive, targetDir)
        else -> error("Archive $name is not supported")
      }
    }
    catch (e: IOException) {
      if (e.message?.contains("No space left on device") == true) {
        throw IOException(buildString {
          appendLine("No space left while extracting $archive to $targetDir")
          appendLine(Files.getFileStore(targetDir).getDiskInfo())
          appendLine(getDiskUsageDiagnostics())
        })
      }

      throw e
    }
  }

  fun compressToTar(source: Path, outputArchive: Path, compressionType: CompressionType? = null) {
    val archiver = if (compressionType == null) ArchiverFactory.createArchiver(ArchiveFormat.TAR)
    else ArchiverFactory.createArchiver(ArchiveFormat.TAR, compressionType)

    val outputArchiveParentDir = outputArchive.parent.apply { createDirectories() }
    archiver.create(outputArchive.nameWithoutExtension, outputArchiveParentDir.toFile(), source.toFile())
  }

  fun unpackTarGz(tarFile: File, targetDir: File) {
    targetDir.deleteRecursively()
    unpackTarGz(tarFile.toPath(), targetDir.toPath())
  }

  /**
   * Delete [Path] recursively quietly, suppressing any exceptions that occur while attempting to read, open, or delete any entries under
   * the given file tree.
   */
  @OptIn(ExperimentalPathApi::class)
  fun Path.deleteRecursivelyQuietly(): Boolean {
    val result = runCatching { deleteRecursively() }
    result.onFailure { error ->
      logError("Failed to delete $this", error)
    }
    return result.isSuccess
  }

  fun Path.listDirectoryEntriesQuietly(): List<Path>? = runCatching { listDirectoryEntries() }.getOrNull()

  // TODO: use com.intellij.platform.eel.EelApi.getArchive when it's ready?
  private fun unpackTarGz(tarFile: Path, targetDir: Path) {
    require(tarFile.fileName.toString().endsWith(".tar.gz")) { "File $tarFile must be tar.gz archive" }

    try {
      if (SystemInfo.isWindows) {
        val archiver = ArchiverFactory.createArchiver(ArchiveFormat.TAR, CompressionType.GZIP)
        archiver.extract(tarFile.toFile(), targetDir.toFile())
      }
      else if (SystemInfo.isLinux || SystemInfo.isMac) {
        Files.createDirectories(targetDir)
        ProcessExecutor(
          presentableName = "extract-tar",
          workDir = targetDir,
          timeout = 10.minutes,
          stderrRedirect = ExecOutputRedirect.ToStdOut("tar"),
          args = listOf("tar", "-z", "-x", "-f", tarFile.toAbsolutePath().toString(), "-C", targetDir.toAbsolutePath().toString())
        ).start()
      }
    }
    catch (e: Exception) {
      targetDir.deleteRecursivelyQuietly()
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

  fun Path.getFileOrDirectoryPresentableSize(): String {
    // explicitly call readAttributes() to reduce the number of system calls
    val attributes = readAttributes<BasicFileAttributes>()
    val size: Long = if (attributes.isRegularFile) {
      attributes.size()
    }
    else {
      Files.walk(this).use { pathStream ->
        pathStream.mapToLong { p: Path ->
          val attributes = p.readAttributes<BasicFileAttributes>()
          if (attributes.isRegularFile) {
            attributes.size()
          }
          else 0
        }.sum()
      }
    }
    return size.formatSize()
  }

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

  fun getDiskUsageDiagnostics(): String {
    val paths = GlobalPaths.instance

    return buildString {
      appendLine("Disk usage by integration tests (home ${paths.testHomePath})")
      appendLine(Files.getFileStore(paths.testHomePath).getDiskInfo())
      appendLine()
      appendLine(paths.testHomePath.getDirectoryTreePresentableSizes(3))
      if (paths.localCacheDirectory != paths.testHomePath / "cache") {
        appendLine("Agent persistent cache directory disk usage ${paths.localCacheDirectory}")
        appendLine(paths.localCacheDirectory.getDirectoryTreePresentableSizes(2))
      }
      appendLine()
      appendLine("Directories' size from ${paths.devServerDirectory}")
      appendLine(paths.devServerDirectory.getDirectoryTreePresentableSizes())
    }
  }

  fun Path.isFileUpToDate(): Boolean {
    if (!this.isRegularFile()) {
      logOutput("File $this does not exist")
      return false
    }
    return if (this.fileSize() <= 0) {
      logOutput("File $this is empty")
      false
    }
    else {
      this.isUpToDate()
    }
  }

  fun Path.isDirUpToDate(): Boolean {
    if (!this.isDirectory()) {
      logOutput("Path $this does not exist")
      return false
    }
    return if (this.fileSize() <= 0) {
      logOutput("Project dir $this is empty")
      false
    }
    else {
      this.isUpToDate()
    }
  }

  private fun Path.isUpToDate(): Boolean {
    val lastModified = Files.getLastModifiedTime(this)
    val timeSinceLastModified = Duration.between(lastModified.toInstant(), Instant.now())

    // less then a day ago
    val upToDate = timeSinceLastModified < Duration.ofDays(1)
    if (upToDate) {
      logOutput("$this is up to date")
    }
    else {
      logOutput("$this is not up to date")
    }
    return upToDate
  }

  private fun isSymlink(entry: JBZipEntry): Boolean {
    val fileMode = (entry.externalAttributes ushr 16) and 0xFFFF
    return (fileMode and 0xF000).toInt() == 0xA000
  }
}

fun FileStore.getDiskInfo(): String = buildString {
  appendLine("Disk info of ${name()}")
  appendLine("  Total space: " + totalSpace.formatSize())
  appendLine("  Unallocated space: " + unallocatedSpace.formatSize())
  appendLine("  Usable space: " + usableSpace.formatSize())
}