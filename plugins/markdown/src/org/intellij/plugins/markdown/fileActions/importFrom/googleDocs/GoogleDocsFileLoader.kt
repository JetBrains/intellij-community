// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.importFrom.googleDocs

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.createFile
import com.intellij.util.io.exists
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Service
class GoogleDocsFileLoader {

  @RequiresBackgroundThread
  fun loadFile(credential: Credential, docsId: String): VirtualFile {
    val driveFiles = getAllFilesFromDrive(credential)
    return downloadFileFromDrive(driveFiles, docsId)
  }

  @RequiresBackgroundThread
  private fun getAllFilesFromDrive(credential: Credential): Drive.Files {
    val httpTransport = GoogleNetHttpTransport.newTrustedTransport()

    return Drive
      .Builder(httpTransport, jsonFactory, credential)
      .build()
      .files()
  }

  @RequiresBackgroundThread
  private fun downloadFileFromDrive(driveFiles: Drive.Files, docsId: String): VirtualFile {
    val outputStream = ByteArrayOutputStream()
    val fileName = with(driveFiles) {
      export(docsId, mimeType).executeAndDownloadTo(outputStream)
      get(docsId).setFields("id, name").execute().name
    }

    val tempFile = File.createTempFile(fileName, ".docx").apply {
      outputStream().use { outputStream.writeTo(it) }
    }

    val renamedFile = moveContentFromTempFile(tempFile, fileName)

    return VfsUtil.findFileByIoFile(renamedFile, true)!!
  }

  private fun moveContentFromTempFile(tempFile: File, fileName: String): File {
    try {
      val targetFilePath = File(tempFile.parent, "$fileName.docx").toPath()

      return Files
        .move(tempFile.toPath(), targetFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE).apply {
          if (!exists()) createFile()
        }.toFile()
    }
    catch (e: IOException) {
      LOG.error(e)
      return tempFile
    }
  }

  companion object {
    private const val mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    private val jsonFactory: JsonFactory get() = JacksonFactory.getDefaultInstance()
    private val LOG = logger<GoogleDocsFileLoader>()
  }
}
