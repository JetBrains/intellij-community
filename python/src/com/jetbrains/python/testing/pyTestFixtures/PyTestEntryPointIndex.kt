// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.externalizer.StringCollectionExternalizer
import org.ini4j.Ini
import java.io.IOException
import java.io.StringReader

/**
 * The entry point group that registers a pytest plugin.
 * pytest loads the object of each entry point of this group from the installed distributions.
 */
internal const val PYTEST_PLUGIN_ENTRY_POINT_GROUP: String = "pytest11"

private const val ENTRY_POINTS_FILE = "entry_points.txt"

private val DISTRIBUTION_METADATA_SUFFIXES = listOf(".dist-info", ".egg-info")

/**
 * Indexes the `pytest11` entry points of the `entry_points.txt` file of each `.dist-info` and `.egg-info` directory.
 *
 * The only key is [PYTEST_PLUGIN_ENTRY_POINT_GROUP]. Its value is the object references of the group in one file, as
 * `pytest_django.plugin` or `pkg:plugin`. The extras of a reference, as `[extra]`, are not in the value.
 */
internal class PyTestEntryPointIndex : FileBasedIndexExtension<String, List<String>>() {
  override fun getName(): ID<String, List<String>> = NAME

  override fun getIndexer(): DataIndexer<String, List<String>, FileContent> = DataIndexer { content ->
    val objectReferences = readPytestPluginObjectReferences(content.contentAsText)
    if (objectReferences.isEmpty()) emptyMap() else mapOf(PYTEST_PLUGIN_ENTRY_POINT_GROUP to objectReferences)
  }

  override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

  override fun getValueExternalizer(): DataExternalizer<List<String>> = StringCollectionExternalizer.STRING_LIST_EXTERNALIZER

  override fun getInputFilter(): FileBasedIndex.InputFilter = FileBasedIndex.InputFilter { file ->
    val metadataDirectory = file.parent?.name
    file.name == ENTRY_POINTS_FILE && metadataDirectory != null && DISTRIBUTION_METADATA_SUFFIXES.any { metadataDirectory.endsWith(it) }
  }

  override fun dependsOnFileContent(): Boolean = true

  override fun getVersion(): Int = 0

  companion object {
    private val NAME: ID<String, List<String>> = ID.create("Py.pytest.entry.points")

    /**
     * Returns the object references of the `pytest11` entry points of the distributions in [scope].
     */
    fun getPytestPluginObjectReferences(scope: GlobalSearchScope): List<String> =
      FileBasedIndex.getInstance().getValues(NAME, PYTEST_PLUGIN_ENTRY_POINT_GROUP, scope).flatten().distinct()
  }
}

/**
 * Reads the object references of the `pytest11` group from the [text] of an `entry_points.txt` file.
 *
 * An object reference is `module` or `module:attribute`, with optional extras, as `module:attribute [extra]`.
 */
private fun readPytestPluginObjectReferences(text: CharSequence): List<String> {
  val ini = Ini().apply {
    config.isMultiOption = true
    config.isTree = false
    config.isEscape = false
    config.isEmptyOption = true
  }
  try {
    ini.load(StringReader(text.toString()))
  }
  catch (e: IOException) {
    fileLogger().debug("Cannot read the entry points", e)
    return emptyList()
  }
  val section = ini[PYTEST_PLUGIN_ENTRY_POINT_GROUP] ?: return emptyList()
  return section.keys.flatMap { name -> section.getAll(name).orEmpty() }.mapNotNull { value ->
    value?.substringBefore('[')?.trim()?.takeIf { it.isNotEmpty() }
  }
}
