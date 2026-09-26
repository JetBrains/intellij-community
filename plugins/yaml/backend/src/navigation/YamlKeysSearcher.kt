// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.navigation

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.cache.CacheManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.util.gist.GistManager
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.EnumeratorStringDescriptor
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.YAMLUtil
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YamlRecursivePsiElementVisitor
import java.io.DataInput
import java.io.DataOutput

internal fun findYamlKeysByPattern(searchedKeyParts: List<String>, searchScope: GlobalSearchScope, project: Project): Sequence<YamlKeyWithFile> {
  val fileTypeRestrictedScope = GlobalSearchScope.getScopeRestrictedByFileTypes(searchScope, YAMLFileType.YML)

  return searchedKeyParts
    .asSequence()
    .flatMap { searchedKeyPart -> findYamlFilesWithWord(searchedKeyPart, fileTypeRestrictedScope, project) }
    .onEach { ProgressManager.checkCanceled() }
    .distinct()
    .flatMap(::mapGistData)
}

private fun findYamlFilesWithWord(keyPart: String, searchScope: GlobalSearchScope, project: Project): Sequence<YAMLFile> {
  return CacheManager.getInstance(project)
    .getFilesWithWord(keyPart, UsageSearchContext.IN_CODE, searchScope, false)
    .asSequence()
    .filterIsInstance<YAMLFile>()
}

internal class YamlKeyWithFile(val key: String, val offset: Int, val file: VirtualFile)

private fun mapGistData(file: PsiFile): Sequence<YamlKeyWithFile> {
  return YamlGistHolder.getInstance().concatenatedKeysGist.getFileData(file)
    .asSequence()
    .map { keyAndOffset -> YamlKeyWithFile(keyAndOffset.key, keyAndOffset.offset, file.virtualFile) }
}

internal typealias YamlConcatenatedKeys = List<YamlConcatenatedKey>

internal data class YamlConcatenatedKey(val key: String, val offset: Int)

@Service(Service.Level.APP)
private class YamlGistHolder {
  companion object {
    fun getInstance(): YamlGistHolder = service<YamlGistHolder>()
  }

  val concatenatedKeysGist = GistManager.getInstance()
    .newPsiFileGist("yamlConcatenatedKeysGist",
                    1,
                    YamlKeyExternalizer(),
                    ::computeAllConcatenatedKeys)

  private fun computeAllConcatenatedKeys(file: PsiFile): YamlConcatenatedKeys {
    if (file !is YAMLFile) return emptyList()
    val allKeys = mutableListOf<YamlConcatenatedKey>()

    file.accept(object : YamlRecursivePsiElementVisitor() {
      override fun visitKeyValue(keyValue: YAMLKeyValue) {
        val key = keyValue.key
        if (key != null) {
          val concatenatedKey = YAMLUtil.getConfigFullName(keyValue)
          allKeys.add(YamlConcatenatedKey(concatenatedKey, key.textOffset))
        }
        super.visitKeyValue(keyValue)
      }

      override fun visitSequence(sequence: YAMLSequence) {
        // Do not visit children
      }
    })
    return allKeys
  }

  private inner class YamlKeyExternalizer : DataExternalizer<YamlConcatenatedKeys> {
    override fun save(dataOutput: DataOutput, value: YamlConcatenatedKeys) {
      writeListOfObjects(dataOutput, value) {
        EnumeratorStringDescriptor.INSTANCE.save(dataOutput, it.key)
        DataInputOutputUtil.writeINT(dataOutput, it.offset)
      }
    }

    override fun read(dataInput: DataInput): YamlConcatenatedKeys {
      return readListOfObjects(dataInput) {
        YamlConcatenatedKey(EnumeratorStringDescriptor.INSTANCE.read(dataInput), DataInputOutputUtil.readINT(dataInput))
      }
    }

    private fun <T> writeListOfObjects(dataOutput: DataOutput, objects: List<T>, objectWriter: (T) -> Unit) {
      DataInputOutputUtil.writeINT(dataOutput, objects.size)
      for (singleObject in objects) {
        objectWriter(singleObject)
      }
    }

    private fun <T> readListOfObjects(dataInput: DataInput, objectReader: () -> T): List<T> {
      val size = DataInputOutputUtil.readINT(dataInput)

      val objects = ArrayList<T>(size)
      for (i in 0 until size) {
        objects.add(objectReader())
      }
      return objects
    }
  }
}