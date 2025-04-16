// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.concurrency.JobLauncher
import com.intellij.diagnostic.CoreAttachmentFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.SerializationManagerImpl
import com.intellij.psi.stubs.SerializedStubTree
import com.intellij.psi.stubs.StubForwardIndexExternalizer
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.util.Processor
import com.intellij.util.Processors
import com.intellij.util.SystemProperties
import com.intellij.util.indexing.*
import com.intellij.util.indexing.diagnostic.dump.IndexContentDiagnostic
import com.intellij.util.indexing.diagnostic.dump.IndexContentDiagnosticDumper
import com.intellij.util.indexing.diagnostic.dump.paths.IndexedFilePath
import com.intellij.util.indexing.diagnostic.dump.paths.IndexedFilePaths
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePaths
import com.intellij.util.indexing.diagnostic.dump.paths.hasPresentablePathMatching
import com.intellij.util.indexing.impl.storage.IndexStorageLayoutLocator
import com.intellij.util.indexing.impl.storage.VfsAwareMapReduceIndex
import com.intellij.util.indexing.impl.storage.VfsAwareMapReduceIndex.IndexerIdHolder
import com.intellij.util.progress.ConcurrentTasksProgressManager
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import com.jetbrains.performancePlugin.utils.errors.ErrorCollector
import com.jetbrains.performancePlugin.utils.errors.ToDirectoryWritingErrorCollector
import com.jetbrains.performancePlugin.utils.indexes.CurrentIndexedFileResolver
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.readText

internal const val PREFIX = AbstractCommand.CMD_PREFIX + "compareIndices"
private val LOG = logger<CompareIndices>()

/**
 * Fully compares two indexes built for the same project: the *stored* index and the *current* index,
 * which were built for the *old project* and the *new project*, respectively.
 * The old project is the same as the current project except that it might have been located in another directory.
 * All files' contents of the old project are equivalent to the current project.
 *
 * The stored index was dumped on disk with [StoreIndices] command.
 * The stored index may contain data irrelevant to the old project (that extra data may have belonged to an irrelevant project).
 * The [StoreIndices] command provides list of files that belonged to the old project (see [IndexContentDiagnostic.projectIndexedFileProviderDebugNameToFileIds]).
 *
 * The current index is available in the currently running IDE via [FileBasedIndex] API.
 *
 * All data belonging to the old project's index must also be present in the current project's index:
 * all triplets `(file, key, value)` of the stored index, which belonged to the old project,
 * must be present in the current index and belong to the current project.
 *
 * The current index must not contain extra data for the current project files, as compared to the stored index.
 * But the current index is allowed to contain extra data for irrelevant files.
 *
 * Compares the stored indices with the last created indices, which are in the current moment on the disk.
 * The folder with the stored indices should be defined by the parameter '-Dcompare.indices.command.stored.indexes.directory'.
 */
internal class CompareIndices(text: String, line: Int) : AbstractCommand(text, line) {

  private companion object {
    private const val LIMIT_OF_ERRORS_PER_COLLECTOR = 100
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback = ActionCallbackProfilerStopper()
    val storedIndexDir = getStoredIndicesDirectory()

    val project = context.project
    DumbService.getInstance(project).smartInvokeLater {
      object : Task.Modal(project, PerformanceTestingBundle.message("comparing.indexes"), false) {
        override fun run(indicator: ProgressIndicator) {
          if (!StubForwardIndexExternalizer.USE_SHAREABLE_STUBS) {
            actionCallback.reject(
              "Index comparison available only with with -D${StubForwardIndexExternalizer.USE_SHAREABLE_STUBS_PROP}=true")
            return
          }
          try {
            compareIndexes(storedIndexDir, indicator, project)
            actionCallback.setDone()
          }
          catch (e: Throwable) {
            LOG.error(e)
            actionCallback.reject(e.message)
          }
        }
      }.queue()
    }
    return actionCallback.toPromise()
  }

  private fun getStoredIndicesDirectory(): Path {
    val property = System.getProperty("compare.indices.command.stored.indexes.directory")
    return checkNotNull(property) { "Path to stored indices is not specified" }.let { Paths.get(it) }
  }

  private fun getFailureDiagnosticDirectory(): Path {
    val property = System.getProperty("compare.indices.command.failure.diagnostic.directory")
    if (property != null) {
      return Paths.get(property)
    }
    return FileUtil.createTempDirectory("compare-indices", "failure").toPath()
  }

  /**
   * Patterns of files to be ignored from comparison.
   * Can be used to ignore transient files that change every time the project is open.
   *
   * Examples:
   * - `<project home>/some/tvolatile-file.txt`
   * - `<project home>/build/\*`
   * - `*.txt`
   */
  private val ignoredFilesPatterns: List<String> by lazy {
    val listPath = System.getProperty("compare.indices.list.of.files.to.ignore.from.comparison") ?: return@lazy emptyList<String>()
    val patterns = Paths.get(listPath).readText().lines().map { it.trim() }.filterNot { it.isEmpty() }
    LOG.info("The following files will be ignored from indexes comparison:\n" + patterns.joinToString(separator = "\n"))
    patterns
  }

  private val ignoredPatternsForReporting: List<Pair<String, String>> by lazy {
    val fileWithPatterns = System.getProperty("compare.indices.list.of.patterns.to.ignore.from.reporting") ?: return@lazy emptyList()
    val patternsList = mutableListOf<Pair<String, String>>()
    Paths.get(fileWithPatterns).readText().lines().map { it.trim() }.filterNot { it.isEmpty() }.forEach {
      val attrs = it.split(" ")
      patternsList.add(Pair(attrs[0], attrs[1]))
    }
    LOG.info("The following patterns will be ignored from failure reporting: ")
    patternsList.forEach { LOG.info("Files .${it.first} in index ${it.second}") }
    patternsList
  }

  /**
   * File types for which Stub tree is not built (but only the forward index).
   * For all such file types [com.intellij.psi.stubs.SerializedStubTree] has [com.intellij.psi.stubs.SerializedStubTree.NO_STUB]
   * as result of [com.intellij.psi.stubs.SerializedStubTree.getStub].
   */
  private val fileTypesWithNoStubTree: Set<String> by lazy {
    System.getProperty("compare.indices.file.types.with.no.stub.tree", "").split(",").filterNot { it.isEmpty() }.toSet()
  }

  private fun compareIndexes(storedIndexDir: Path, indicator: ProgressIndicator, project: Project) {
    val failureDiagnosticDirectory = getFailureDiagnosticDirectory()
    (FileBasedIndex.getInstance() as FileBasedIndexImpl).flushIndexes()

    indicator.text = IndexingBundle.message("index.content.diagnostic.reading")
    val storedIndexContentDiagnostic = IndexContentDiagnosticDumper.readFrom(StoreIndices.getFileForDiagnostic(storedIndexDir))
    val storedIndexedFileResolver = StoredIndexedFileResolver(storedIndexContentDiagnostic)

    val filesErrorCollector = ToDirectoryWritingErrorCollector(
      "resolve-files",
      failureDiagnosticDirectory.resolve("files-resolution-errors"),
      LIMIT_OF_ERRORS_PER_COLLECTOR
    )
    val resolvedFiles = resolveFiles(storedIndexedFileResolver, filesErrorCollector, indicator, project)
    if (filesErrorCollector.numberOfErrors > 0) {
      throw IllegalArgumentException("Some files cannot be resolved. See <failure-diagnostics-dir>/files-resolution-errors")
    }

    LOG.info("Stored index directory contains: ${StoreIndices.dumpDirectory(storedIndexDir)}")

    val idsToCompare = FileBasedIndexExtension.EXTENSION_POINT_NAME.extensionList.asSequence()
      .filter {
        it.dependsOnFileContent()
        // https://youtrack.jetbrains.com/issue/IDEA-255298
        && it.name.name != "HashFragmentIndex"
      }
      .map { it.name }.toList()

    LOG.info("Comparing indexes concurrently")
    val errorCollectors = idsToCompare.associateWith {
      ToDirectoryWritingErrorCollector(
        it.name,
        failureDiagnosticDirectory.resolve("errors-for-${it.name}"),
        LIMIT_OF_ERRORS_PER_COLLECTOR
      )
    }
    val finishedCounter = AtomicInteger()
    val comparisonTaskProgressManager = ConcurrentTasksProgressManager(indicator, idsToCompare.size)
    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(idsToCompare, indicator, Processor { id ->
      val subIndicator = comparisonTaskProgressManager.createSubTaskIndicator(1)
      val (extension, disposable) = runReadAction { findFileBasedIndexExtension(id, storedIndexDir) }
      try {
        val errorCollector = errorCollectors.getValue(id)
        compareCurrentAndStoredIndexData(
          resolvedFiles,
          extension,
          storedIndexDir,
          subIndicator,
          errorCollector,
          project
        )
      }
      catch (e: Exception) {
        LOG.warn("Index comparison for ${id.name} has failed", e)
      }
      finally {
        runCatching { Disposer.dispose(disposable) }.onFailure { LOG.warn(it) }
      }
      LOG.info("Index comparison has finished for ${finishedCounter.incrementAndGet()} / ${idsToCompare.size}")
      true
    })

    val failedIndexes = errorCollectors.filterValues { it.numberOfErrors > 0 }.map { it.key }
    if (failedIndexes.isNotEmpty()) {
      throw RuntimeException(
        "Comparison has failed for indexes [${failedIndexes.joinToString { it.name }}]. Details are saved to $failureDiagnosticDirectory")
    }
    LOG.info("Success. All indices are equal: ${idsToCompare.joinToString { it.name }}")
  }

  private fun findFileBasedIndexExtension(id: ID<*, *>, storedIndexDir: Path): Pair<FileBasedIndexExtension<*, *>, Disposable> {
    return if (id.name == "Stubs") {
      val storedSerializationManager = SerializationManagerImpl(storedIndexDir.resolve("rep.names"), true)
      // Stored indexes must have been dumped with -D${com.intellij.psi.stubs.StubForwardIndexExternalizer.USE_SHAREABLE_STUBS_PROP}=true option specified
      // to make stubs be serialized in shareable form (when [ID.getUniqueId()] is not serialized but the [ID.getName()] ID name is).
      val nameStorageDump = storedSerializationManager.dumpNameStorage()
      LOG.info("stored stub element name storage $nameStorageDump")
      val stubForwardIndexExternalizer = StubForwardIndexExternalizer.createFileLocalExternalizer()
      val storedStubUpdatingIndex = StubUpdatingIndex(stubForwardIndexExternalizer,
                                                      storedSerializationManager) as FileBasedIndexExtension<*, *>
      storedStubUpdatingIndex to storedSerializationManager
    }
    else {
      val usualExtension = FileBasedIndexExtension.EXTENSION_POINT_NAME.findFirstSafe { ex -> ex.name == id }!!
      val emptyDisposable = Disposer.newDisposable()
      usualExtension to emptyDisposable
    }
  }

  private data class FileDescriptor(
    val originalFilePath: IndexedFilePath,
    val currentFile: VirtualFile,
  )

  private fun resolveFiles(
    storedIndexedFileResolver: StoredIndexedFileResolver,
    errorCollector: ErrorCollector,
    indicator: ProgressIndicator,
    project: Project,
  ): List<FileDescriptor> {
    val allCurrentFiles = CurrentIndexedFileResolver.getAllToBeIndexedFilesInProject(project, indicator).values.flatMapTo(
      hashSetOf()) { it }
    indicator.text = PerformanceTestingBundle.message("compare.indexes.resolving.files")
    indicator.isIndeterminate = false
    val fileDescriptors = Collections.synchronizedList(arrayListOf<FileDescriptor>())
    val finished = AtomicInteger()
    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(
      storedIndexedFileResolver.originalIndexedFiles.toList(),
      indicator,
      Processor { originalFilePath ->
        if (ignoredFilesPatterns.any { originalFilePath.portableFilePath.hasPresentablePathMatching(it) }) {
          return@Processor true
        }
        val currentFile: VirtualFile = errorCollector.runCatchingError {
          storedIndexedFileResolver.findFileInCurrentProject(originalFilePath, project)
        } ?: return@Processor true
        indicator.fraction = finished.incrementAndGet().toDouble() / storedIndexedFileResolver.originalIndexedFiles.size
        indicator.text2 = currentFile.url
        if (currentFile in allCurrentFiles) {
          fileDescriptors += FileDescriptor(originalFilePath, currentFile)
        }
        true
      }
    )
    return fileDescriptors
  }

  private fun <K, V> compareCurrentAndStoredIndexData(
    resolvedFiles: List<FileDescriptor>,
    extension: FileBasedIndexExtension<K, V>,
    storedIndexDir: Path,
    indicator: ProgressIndicator,
    errorCollector: ErrorCollector,
    project: Project,
  ) {
    val indexId = extension.name
    indicator.text = PerformanceTestingBundle.message("compare.indexes.comparing.index", indexId.name)
    indicator.text2 = PerformanceTestingBundle.message("compare.indexes.preparing.indexes")
    indicator.isIndeterminate = true

    val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
    runReadAction { fileBasedIndex.ensureUpToDate(indexId, project, GlobalSearchScope.allScope(project)) }
    val currentIndex = fileBasedIndex.getIndex(indexId)

    val storedIndex = openStoredIndex(storedIndexDir, extension)
    try {
      indicator.isIndeterminate = false
      indicator.fraction = 0.0
      indicator.text2 = ""

      check(extension.needsForwardIndexWhenSharing() || extension !is SingleEntryFileBasedIndexExtension) {
        "Index ${indexId.name} is SingleEntryFileBasedIndexExtension and does not need forward index. " +
        "This is a wrong index because SingleEntryFileBasedIndexExtension by its nature effectively consists of only the forward index."
      }

      if (extension.needsForwardIndexWhenSharing()) {
        compareForwardIndexes(extension, resolvedFiles, currentIndex, storedIndex, errorCollector, indicator, project)
      }
      if (extension !is SingleEntryFileBasedIndexExtension) {
        compareInvertedIndexes(extension, resolvedFiles, storedIndex, currentIndex, errorCollector, indicator, project)
      }
    }
    finally {
      storedIndex.dispose()
    }
  }

  private fun <K, V> compareForwardIndexes(
    extension: FileBasedIndexExtension<K, V>,
    resolvedFiles: List<FileDescriptor>,
    currentIndex: UpdatableIndex<K, V, FileContent, *>,
    storedIndex: UpdatableIndex<K, V, FileContent, *>,
    errorCollector: ErrorCollector,
    indicator: ProgressIndicator,
    project: Project,
  ) {
    val indexId = extension.name
    indicator.text = PerformanceTestingBundle.message("compare.indexes.comparing.forward.index", indexId.name)
    for ((finished, resolvedFile) in resolvedFiles.withIndex()) {
      indicator.fraction = finished.toDouble() / resolvedFiles.size
      indicator.text2 = resolvedFile.currentFile.url
      compareFileData(resolvedFile, currentIndex, storedIndex, extension, errorCollector, project)
    }
  }

  private fun <K, V> compareInvertedIndexes(
    extension: FileBasedIndexExtension<K, V>,
    resolvedFiles: List<FileDescriptor>,
    storedIndex: UpdatableIndex<K, V, FileContent, *>,
    currentIndex: UpdatableIndex<K, V, FileContent, *>,
    errorCollector: ErrorCollector,
    indicator: ProgressIndicator,
    project: Project,
  ) {
    val indexId = extension.name
    indicator.text = PerformanceTestingBundle.message("compare.indexes.comparing.inverted.index", indexId.name)
    indicator.text2 = PerformanceTestingBundle.message("compare.indexes.comparing.inverted.index.collecting.keys")
    indicator.isIndeterminate = true

    val allStoredKeys = hashSetOf<K>()
    errorCollector.runCatchingError {
      runReadAction {
        storedIndex.processAllKeys(Processors.cancelableCollectProcessor(allStoredKeys), GlobalSearchScope.allScope(project), null)
      }
    }

    val allCurrentKeys = hashSetOf<K>()
    errorCollector.runCatchingError {
      runReadAction {
        currentIndex.processAllKeys(Processors.cancelableCollectProcessor(allCurrentKeys), GlobalSearchScope.allScope(project), null)
      }
    }

    checkNoKeysAreMissing(extension, storedIndex, resolvedFiles, allStoredKeys, allCurrentKeys, errorCollector, project)

    val originalFileIdToFileDescriptor = resolvedFiles.associateBy { it.originalFilePath.originalFileSystemId }
    indicator.isIndeterminate = false
    indicator.fraction = 0.0
    for ((index, storedKey: K) in allStoredKeys.withIndex()) {
      indicator.fraction = index.toDouble() / allStoredKeys.size

      val storedFileIdToValue: MutableMap<Int, V?> = mutableMapOf()
      errorCollector.runCatchingError {
        runReadAction {
          storedIndex.withDataOf(storedKey!!) { container ->
            storedFileIdToValue.putAll(container.toMap())
            true
          }
        }
      }
      if (storedFileIdToValue.isEmpty()) continue

      val currentFileIdToValue: MutableMap<Int, V?> = mutableMapOf()
      errorCollector.runCatchingError {
        runReadAction {
          currentIndex.withDataOf(storedKey!!) { container ->
            currentFileIdToValue.putAll(container.toMap())
            true
          }
        }
      }
      if (currentFileIdToValue.isEmpty()) continue

      for ((storedFileId, storedValue) in storedFileIdToValue) {
        val fileDescriptor = originalFileIdToFileDescriptor[storedFileId] ?: continue
        val currentFileId = FileBasedIndexImpl.getFileId(fileDescriptor.currentFile)
        val currentValue = currentFileIdToValue[currentFileId]
        if (IndexDataComparer.areValuesTheSame(extension, storedValue, currentValue)) {
          continue
        }
        if (ignoredPatternsForReporting.isEmpty() || !isKnownError(extension,
                                                                   fileDescriptor.originalFilePath.portableFilePath.presentablePath)) {
          errorCollector.runCatchingError {
            val message = buildFileDataMismatchMessage(
              "Values mismatch for key ${IndexDataPresenter.getPresentableIndexKey(storedKey)}",
              extension,
              fileDescriptor,
              project
            )
            val attachments = arrayListOf<Attachment>()
            attachments += Attachment("expected-value.txt", IndexDataPresenter.getPresentableIndexValue(storedValue))
            attachments += Attachment("actual-value.txt", IndexDataPresenter.getPresentableIndexValue(currentValue))
            attachments += createAttachmentsForActualFile(fileDescriptor)
            throw RuntimeExceptionWithAttachments(message, *attachments.toTypedArray())
          }
        }
      }
    }
  }

  private fun <K, V> checkNoKeysAreMissing(
    extension: FileBasedIndexExtension<K, V>,
    storedIndex: UpdatableIndex<K, V, FileContent, *>,
    resolvedFiles: List<FileDescriptor>,
    allStoredKeys: Set<K>,
    allCurrentKeys: Set<K>,
    errorCollector: ErrorCollector,
    project: Project,
  ) {
    val missingKeys = allStoredKeys - allCurrentKeys
    if (missingKeys.isNotEmpty()) {
      val originalFileIdToFileDescriptor = resolvedFiles.associateBy { it.originalFilePath.originalFileSystemId }
      for (missingKey in missingKeys) {
        val storedFileIdToValue: MutableMap<Int, V?> = mutableMapOf()
        errorCollector.runCatchingError {
          runReadAction {
            storedIndex.withDataOf(missingKey!!) { container ->
              storedFileIdToValue.putAll(container.toMap().filterKeys { it in originalFileIdToFileDescriptor })
              true
            }
          }
        }
        if (storedFileIdToValue.isEmpty()) {
          continue
        }

        val fileDescriptor = originalFileIdToFileDescriptor[storedFileIdToValue.entries.iterator().next().key]

        if (ignoredPatternsForReporting.isEmpty() || !isKnownError(extension, IndexDataPresenter.getPresentableIndexKey(missingKey))) {
          if (!isKnownError(extension, fileDescriptor!!)) {
            errorCollector.runCatchingError {
              val message = buildString {
                appendLine(
                  "Index ${extension.name.name}: key is unknown to actual index data: ${
                    IndexDataPresenter.getPresentableIndexKey(missingKey)
                  }"
                )
                appendLine("The key must be present in the index for the following files: ")
                for ((storedFileId, _) in storedFileIdToValue) {
                  val fileDescriptor = originalFileIdToFileDescriptor[storedFileId] ?: continue
                  appendLine(
                    buildFileDataMismatchMessage(
                      "Key is not available in the actual index for the file",
                      extension,
                      fileDescriptor,
                      project
                    ).withIndent("  ")
                  )
                  appendLine()
                }
              }

              val attachments = arrayListOf<Attachment>()
              attachments += Attachment("key.txt", IndexDataPresenter.getPresentableIndexKey(missingKey))
              var nextMismatchedFileId = 1
              for ((storedFileId, storedValue) in storedFileIdToValue) {
                val fileDescriptor = originalFileIdToFileDescriptor[storedFileId] ?: continue
                val pathPrefix = "file-and-value-${nextMismatchedFileId++}/"
                attachments += Attachment("${pathPrefix}value.txt", IndexDataPresenter.getPresentableIndexValue(storedValue))
                attachments += createAttachmentsForActualFile(fileDescriptor, pathPrefix = pathPrefix)
              }
              throw RuntimeExceptionWithAttachments(message, *attachments.toTypedArray())
            }
          }
        }
      }
    }
  }

  private fun <V> ValueContainer<V>.toMap(): Map</* File ID */ Int, V?> {
    val map = hashMapOf<Int, V>()
    forEach { fileId, value ->
      if (value != null) {
        map[fileId] = value
      }
      true
    }
    return map
  }

  @Synchronized
  private fun <K, V> openStoredIndex(
    storedIndexDir: Path,
    extension: FileBasedIndexExtension<K, V>,
  ): VfsAwareMapReduceIndex<K, V, IndexerIdHolder> {
    val propertyName = "index_root_path"
    val oldValue = System.setProperty(propertyName, storedIndexDir.toAbsolutePath().toString())
    try {
      return VfsAwareMapReduceIndex<K, V, IndexerIdHolder>(extension, IndexStorageLayoutLocator.getLayout(extension))
    }
    finally {
      SystemProperties.setProperty(propertyName, oldValue)
    }
  }

  private fun <K, V> compareFileData(
    fileDescriptor: FileDescriptor,
    currentIndex: UpdatableIndex<K, V, FileContent, *>,
    storedIndex: UpdatableIndex<K, V, FileContent, *>,
    extension: FileBasedIndexExtension<K, V>,
    errorCollector: ErrorCollector,
    project: Project,
  ) {
    val storedData: Map<K, V> = errorCollector.runCatchingError {
      runReadAction { storedIndex.getIndexedFileData(fileDescriptor.originalFilePath.originalFileSystemId) }
    } ?: return

    val currentData: Map<K, V> = errorCollector.runCatchingError {
      runReadAction { currentIndex.getIndexedFileData(FileBasedIndex.getFileId(fileDescriptor.currentFile)) }
    } ?: return

    errorCollector.runCatchingError {
      assertForwardIndexDataAreTheSameForFile(
        fileDescriptor,
        extension,
        storedData,
        currentData,
        project
      )
    } ?: return
  }

  private fun <K, V> assertForwardIndexDataAreTheSameForFile(
    fileDescriptor: FileDescriptor,
    extension: FileBasedIndexExtension<K, V>,
    expectedData0: Map<K, V>,
    actualData0: Map<K, V>,
    project: Project,
  ) {
    val expectedData: Map<K, V>
    val actualData: Map<K, V>
    @Suppress("UNCHECKED_CAST")
    if (extension.name == StubUpdatingIndex.INDEX_ID && fileDescriptor.originalFilePath.fileType in fileTypesWithNoStubTree) {
      expectedData = expectedData0.mapValues { (it.value as SerializedStubTree).withoutStub() } as Map<K, V>
      actualData = actualData0.mapValues { (it.value as SerializedStubTree).withoutStub() } as Map<K, V>
    }
    else {
      expectedData = expectedData0
      actualData = actualData0
    }

    if (IndexDataComparer.areIndexedDataOfFileTheSame(extension, expectedData, actualData)) {
      return
    }

    if (ignoredPatternsForReporting.isEmpty() || !isKnownError(extension,
                                                               fileDescriptor.originalFilePath.portableFilePath.presentablePath)) {
      val message = buildFileDataMismatchMessage(
        "Indexed data maps do not match for ${fileDescriptor.originalFilePath.portableFilePath.presentablePath}",
        extension,
        fileDescriptor,
        project
      )

      val expectedDataAttachment = if (expectedData.isEmpty()) {
        Attachment("expected-data-is-empty.txt", "")
      }
      else {
        Attachment("expected-data.txt", IndexDataPresenter.getPresentableKeyValueMap(expectedData))
      }
      val actualDataAttachment = if (actualData.isEmpty()) {
        Attachment("actual-data-is-empty.txt", "")
      }
      else {
        Attachment("actual-data.txt", IndexDataPresenter.getPresentableKeyValueMap(actualData))
      }
      val attachments = arrayListOf(expectedDataAttachment, actualDataAttachment)

      attachments += createAttachmentsForActualFile(fileDescriptor)
      attachments += createAttachmentsForExpectedFile(fileDescriptor)

      throw RuntimeExceptionWithAttachments(message, *attachments.toTypedArray())
    }
  }

  private fun isKnownError(
    extension: FileBasedIndexExtension<*, *>,
    fileNameOrIndexData: String,
  ): Boolean {
    val fileExtension = File(fileNameOrIndexData).extension
    ignoredPatternsForReporting.forEach {
      if (it.first == extension.name.name && it.second == fileExtension) {
        LOG.info("Reporting index mismatch of $fileNameOrIndexData in ${extension.name.name} extension is ignored")
        return true
      }
    }
    return false
  }

  private fun isKnownError(
    extension: FileBasedIndexExtension<*, *>,
    fileDescriptor: FileDescriptor,
  ): Boolean {
    val fileExtension = fileDescriptor.currentFile.extension
    ignoredPatternsForReporting.forEach {
      if (it.first == extension.name.name && it.second == fileExtension) {
        LOG.info("Reporting index mismatch of $fileExtension in ${extension.name.name} extension is ignored")
        return true
      }
    }
    return false
  }

  private fun createAttachmentsForActualFile(fileDescriptor: FileDescriptor, pathPrefix: String = ""): List<Attachment> =
    findFileAndAllRelevantSiblings(fileDescriptor.currentFile).map { file ->
      CoreAttachmentFactory.createAttachment("${pathPrefix}actual-file/${file.name}", file)
    }

  private fun createAttachmentsForExpectedFile(fileDescriptor: FileDescriptor, pathPrefix: String = ""): List<Attachment> {
    val expectedFile = VirtualFileManager.getInstance().refreshAndFindFileByUrl(fileDescriptor.originalFilePath.originalFileUrl)
    return if (expectedFile != null) {
      findFileAndAllRelevantSiblings(expectedFile).map { file ->
        CoreAttachmentFactory.createAttachment("${pathPrefix}expected-file/${file.name}", file)
      }
    }
    else {
      emptyList()
    }
  }


  private fun buildFileDataMismatchMessage(
    reason: String,
    extension: FileBasedIndexExtension<*, *>,
    fileDescriptor: FileDescriptor,
    project: Project,
  ) = buildString {
    appendLine("Index mismatch ${extension.name.name} for ${fileDescriptor.originalFilePath.portableFilePath.presentablePath}: $reason")
    appendLine("File of expected data:")
    appendLine(fileDescriptor.originalFilePath.toString().withIndent("  "))
    appendLine("File of actual data:")
    val actualIndexedFilePath = IndexedFilePaths.createIndexedFilePath(fileDescriptor.currentFile, project)
    appendLine(actualIndexedFilePath.toString().withIndent("  "))
    if (doesFileHaveProvidedIndex(fileDescriptor.currentFile, extension, project)) {
      appendLine("  Index ${extension.name.name} of this file is provided by index infrastructure extension")
    }
  }

  private fun doesFileHaveProvidedIndex(file: VirtualFile, extension: FileBasedIndexExtension<*, *>, project: Project): Boolean {
    val fileId = FileBasedIndex.getFileId(file)
    return FileBasedIndexInfrastructureExtension.EP_NAME.extensionList.asSequence()
      .mapNotNull { it.createFileIndexingStatusProcessor(project) }
      .any { it.hasIndexForFile(file, fileId, extension) }
  }

  private fun findFileAndAllRelevantSiblings(file: VirtualFile): List<VirtualFile> {
    if (file.extension != "class") {
      return listOf(file)
    }
    val fullClassName = file.nameWithoutExtension
    val baseClassName = fullClassName.substringBefore("$")
    val parent = file.parent ?: return listOf(file)
    parent.refresh(false, true)
    return parent.children.orEmpty().filter {
      it.extension == "class" && (it.nameWithoutExtension == fullClassName || it.nameWithoutExtension.startsWith("$baseClassName$"))
    }
  }

  private fun String.withIndent(indent: String) = lineSequence().joinToString(separator = "\n") { "$indent$it" }
}

/**
 * Provides access to indexed files that belonged to the old project.
 */
private class StoredIndexedFileResolver(storedIndexContentDiagnostic: IndexContentDiagnostic) {

  private val originalFileIdToIndexedFilePath: Map<Int, IndexedFilePath> =
    storedIndexContentDiagnostic.allIndexedFilePaths.associateBy { it.originalFileSystemId }

  val originalIndexedFiles: Set<IndexedFilePath> =
    storedIndexContentDiagnostic.projectIndexedFileProviderDebugNameToFileIds.values.asSequence().flatten()
      .mapNotNullTo(hashSetOf()) { originalFileIdToIndexedFilePath[it] }

  fun findFileInCurrentProject(filePath: IndexedFilePath, currentProject: Project): VirtualFile {
    val currentFile = PortableFilePaths.findFileByPath(filePath.portableFilePath, currentProject)
    checkNotNull(currentFile) {
      buildString {
        appendLine("File corresponding to the old file system is not found in the current file system.")
        appendLine("Portable file path = ${filePath.portableFilePath.presentablePath}")
        appendLine("Original file URL = ${filePath.originalFileUrl}")
        appendLine("Original file ID = ${filePath.originalFileSystemId}")
      }
    }
    return currentFile
  }
}
