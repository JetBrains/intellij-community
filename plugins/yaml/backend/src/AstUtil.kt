// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml

import com.intellij.lang.LighterAST
import com.intellij.lang.LighterASTNode
import com.intellij.lang.LighterASTTokenNode

/**
 * Visits a top level key:value pairs in YAML AST.
 * You should return `true` from visitor to continue processing and false otherwise.
 */
fun visitTopLevelKeyPairs(tree: LighterAST, visitor: (key: CharSequence, pair: LighterASTNode) -> Boolean) : Boolean {
  val astRoot = tree.root
  for (child in tree.getChildren(astRoot)) {
    if (child.tokenType == YAMLElementTypes.DOCUMENT) {
      for (mapping in tree.getChildren(child)) {
        if (mapping.tokenType == YAMLElementTypes.MAPPING) {
          for (keyPair in tree.getChildren(mapping)) {
            if (keyPair.tokenType == YAMLElementTypes.KEY_VALUE_PAIR) {
              val keyName = getKeyName(tree, keyPair) ?: continue
              if (!visitor.invoke(keyName, keyPair)) return false
            }
          }
        }
      }
    }
  }
  return true
}

private fun getKeyName(tree: LighterAST, keyPair: LighterASTNode): CharSequence? {
  val key = tree.getChildren(keyPair).firstOrNull() as? LighterASTTokenNode? ?: return null
  if (key.tokenType == YAMLTokenTypes.SCALAR_KEY) {
    return key.text
  }
  return null
}