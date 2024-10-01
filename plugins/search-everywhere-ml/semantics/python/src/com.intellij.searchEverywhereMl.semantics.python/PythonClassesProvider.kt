package com.intellij.searchEverywhereMl.semantics.python

import com.intellij.platform.ml.embeddings.indexer.ClassesProvider
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableClass
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.psi.util.childrenOfType
import com.intellij.util.indexing.FileContent
import com.jetbrains.python.psi.PyClass

internal class PythonClassesProvider : ClassesProvider {
  override fun extract(fileContent: FileContent): List<IndexableClass> {
    return fileContent.psiFile.childrenOfType<PyClass>().asSequence()
      .mapNotNull { c -> c.name }
      .filter { name -> name != ANONYMOUS_ID }
      .map { name -> IndexableClass(EntityId(name)) }
      .toList()
  }
}

private const val ANONYMOUS_ID = "<anonymous>"