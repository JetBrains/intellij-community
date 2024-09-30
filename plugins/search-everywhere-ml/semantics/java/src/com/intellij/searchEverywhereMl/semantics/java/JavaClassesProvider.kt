package com.intellij.searchEverywhereMl.semantics.java

import com.intellij.platform.ml.embeddings.indexer.ClassesProvider
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableClass
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile

internal class JavaClassesProvider : ClassesProvider {
  override fun extract(file: PsiFile): List<IndexableClass> {
    return (file as PsiJavaFile).classes.asSequence()
      .mapNotNull { c -> c.name }
      .map { name -> IndexableClass(EntityId(name)) }
      .toList()
  }
}