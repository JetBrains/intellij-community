package com.intellij.searchEverywhereMl.semantics.java

import com.intellij.platform.ml.embeddings.indexer.ClassesProvider
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableClass
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.psi.impl.source.JavaLightTreeUtil
import com.intellij.psi.impl.source.tree.JavaElementType
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.PsiDependentFileContent

internal class JavaClassesProvider : ClassesProvider {
  override fun extract(fileContent: FileContent): List<IndexableClass> {
    val ast = (fileContent as PsiDependentFileContent).lighterAST
    val topLevelNodes = ast.getChildren(ast.root).asSequence()
    val classes = topLevelNodes.filter { node -> node.tokenType == JavaElementType.CLASS }

    val classNames = classes
      .mapNotNull { clazz -> JavaLightTreeUtil.getNameIdentifierText(ast, clazz) }
      .filter { name -> name != ANONYMOUS_ID }

    return classNames
      .map { name -> IndexableClass(EntityId(name)) }
      .toList()
  }
}

private const val ANONYMOUS_ID = "<anonymous>"
