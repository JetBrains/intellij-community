// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.importFrom.googleDocs

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

@Service
class GoogleDocsFileLoader : Disposable {

  fun loadFile(credential: Credential, docsId: String): VirtualFile {
    val driveFiles = getAllFilesFromDrive(credential)
    return exportFileFromDrive(driveFiles, docsId)
  }

  private fun exportFileFromDrive(driveFiles: Drive.Files, docsId: String): VirtualFile {
    val outputStream = ByteArrayOutputStream()
    val fileName = with(driveFiles) {
      export(docsId, mimeType).executeAndDownloadTo(outputStream)
      get(docsId).setFields("id, name").execute().name
    }

    val tempFile = File.createTempFile(fileName, ".docx")
    FileUtil.rename(tempFile, fileName)
    FileOutputStream(tempFile).use { outputStream.writeTo(it) }

    return VfsUtil.findFileByIoFile(tempFile, true)!!
  }

  private fun getAllFilesFromDrive(credential: Credential): Drive.Files {
    val httpTransport = GoogleNetHttpTransport.newTrustedTransport()

    return Drive
      .Builder(httpTransport, jsonFactory, credential)
      .build()
      .files()
  }

  override fun dispose() {}

  companion object {
    private const val mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    private val jsonFactory: JsonFactory get() = JacksonFactory.getDefaultInstance()
  }
}