// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.selucene.backend.providers.lucene

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.FieldType
import org.apache.lucene.document.StringField
import org.apache.lucene.index.IndexOptions
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.div

@Service(Service.Level.PROJECT)
class LuceneIndexer(val project: Project, val coroutineScope: CoroutineScope) {
  private val mutex = Mutex()
  val indexPath: Path? = project.basePath?.let {
    Path(it) / ".idea" / "luceneIndex"
  }

  suspend fun indexAll() {
    val fileIndex = ProjectFileIndex.getInstance(project)

    val files = mutableListOf<IndexableFileInfo>()
    fileIndex.iterateContent { file ->
      ProgressManager.checkCanceled()
      if (!file.isDirectory) {
        files.add(IndexableFileInfo.of(file, project))
      }
      true // continue iteration
    }

    mutex.withLock {
      val writer = createWriter() ?: run {
        LuceneSearcher.LOG.warn("Cannot create Lucene index writer")
        return
      }
      writer.deleteAll()
      indexEntities(files, writer)
      writer.close()
    }
  }

  private fun createWriter(): IndexWriter? {
    val outputDir = indexPath ?: return null
    val indexDirectory = FSDirectory.open(outputDir)
    val analyzer = StandardAnalyzer()
    val config = IndexWriterConfig(analyzer)
    return IndexWriter(indexDirectory, config)
  }

  private fun indexEntities(entities: List<IndexableFileInfo>, writer: IndexWriter) {
    entities.forEach { entity ->
      if (!Files.exists(Paths.get(entity.absolutePath))) return
      val document: Document = getDocument(entity)
      writer.addDocument(document)
    }
    writer.commit()
  }

  @Throws(IOException::class)
  private fun getDocument(fileInfo: IndexableFileInfo): Document {
    val document = Document()

    val tokenizedField = FieldType()
    tokenizedField.setStored(true)
    tokenizedField.setTokenized(true)
    tokenizedField.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)

    val fields = listOf(
      //Field(FILE_SOURCE_ROOT_PATH, fileInfo.sourceRootPath, tokenizedField),
      //StringField(FILE_CONTENT_ROOT_PATH, fileInfo.contentRootPath, Field.Store.YES),
      StringField(FILE_ABSOLUTE_PATH, fileInfo.absolutePath, Field.Store.YES),
      StringField(FILE_NAME, fileInfo.name, Field.Store.YES),
      StringField(FILE_LOWERCASE_NAME, fileInfo.name.lowercase(), Field.Store.YES)
    )

    fields.forEach { document.add(it) }

    return document
  }

  companion object {
    fun getInstance(project: Project): LuceneIndexer = project.service<LuceneIndexer>()

    const val FILE_NAME: String = "fileName"
    const val FILE_LOWERCASE_NAME: String = "fileLowercaseName"
    const val FILE_CONTENT_ROOT_PATH: String = "fileContentRootPath"
    const val FILE_SOURCE_ROOT_PATH: String = "fileSourceRootPath"
    const val FILE_ABSOLUTE_PATH: String = "fileAbsolutePath"
  }
}