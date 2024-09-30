package com.intellij.searchEverywhereMl.semantics.java

import com.intellij.platform.ml.embeddings.indexer.SymbolsProvider
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableSymbol
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.psi.impl.source.JavaLightTreeUtil
import com.intellij.psi.impl.source.tree.JavaElementType
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.PsiDependentFileContent

internal class JavaSymbolsProvider : SymbolsProvider {
  override fun extract(fileContent: FileContent): List<IndexableSymbol> {
    val ast = (fileContent as PsiDependentFileContent).lighterAST
    val topLevelNodes = ast.getChildren(ast.root).asSequence()
    val classes = topLevelNodes.filter { node -> node.tokenType == JavaElementType.CLASS }

    val methods = classes
      .flatMap { classNode -> ast.getChildren(classNode) }
      .filter { node -> node.tokenType == JavaElementType.METHOD }

    val methodNames = methods
      .mapNotNull { method -> JavaLightTreeUtil.getNameIdentifierText(ast, method) }
      .filter { name -> name != ANONYMOUS_ID }

    return methodNames
      .map { name -> IndexableSymbol(EntityId(name)) }
      .toList()
  }
}

private const val ANONYMOUS_ID = "<anonymous>"
