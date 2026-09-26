package com.intellij.ide.starter.utils

import java.net.URI
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object JarUtils {

  fun extractResource(resourceName: String, tempDir: Path): Path {
    val targetDir = tempDir.resolve(resourceName)
    val resourceUrl = JarUtils::class.java.classLoader.getResource(resourceName)
                      ?: throw IllegalStateException("Resource not found: $resourceName")
      Files.createDirectories(targetDir)
    when (resourceUrl.protocol) {
      "jar" -> {
        extractFromJar(resourceUrl, resourceName, targetDir)
      }
      "file" -> {
        val resourceDir = Path.of(resourceUrl.toURI())
        if (Files.isDirectory(resourceDir)) {
          Files.walk(resourceDir)
            .filter { Files.isRegularFile(it) }
            .forEach { filePath ->
              val relativePath = resourceDir.relativize(filePath)
              val targetFile = targetDir.resolve(relativePath)
                Files.createDirectories(targetFile.parent)
                Files.copy(filePath, targetFile, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        else {
          throw IllegalStateException("Resource is not a directory: $resourceName")
        }
      }
      else -> {
        throw IllegalStateException("Unsupported protocol: ${resourceUrl.protocol}")
      }
    }
    return targetDir
  }

  private fun extractFromJar(resourceUrl: URL, resourcePath: String, targetDir: Path) {
    val externalForm = resourceUrl.toExternalForm()
    val bangIndex = externalForm.indexOf("!/")

    val fsUri = URI.create(externalForm.take(bangIndex + 2))

    FileSystems.newFileSystem(fsUri, emptyMap<String, Any>()).use { fs ->
      val entryPath = resourcePath.trimStart('/')
      val basePath = fs.getPath(entryPath)

      Files.walk(basePath)
        .filter { Files.isRegularFile(it) }
        .forEach { pathInJar ->
          val relative = basePath.relativize(pathInJar)
          val target = targetDir.resolve(relative.toString())
          Files.createDirectories(target.parent)
          Files.copy(pathInJar, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
  }
}