@file:JvmName("LighterASTTraversalUtils")
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.navigation

import com.intellij.json.lightTree.LightTreeSearchParameters
import com.intellij.json.lightTree.queryLightAST
import com.intellij.lang.LighterAST
import com.intellij.lang.LighterASTNode
import com.intellij.lang.LighterASTTokenNode
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.impl.source.tree.LightTreeUtil
import com.intellij.psi.tree.TokenSet
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.yaml.YAMLElementTypes
import org.jetbrains.yaml.YAMLTokenTypes

@Internal
internal fun collectYamlTreeData(tree: LighterAST): Map<String, Int> {
  return queryLightAST(tree, LightTreeSearchParameters(YAML_PROPERTY_LIKE_TOKEN_TYPES, true, ::getUnquotedKeyFromYamlKeyValue))
    .associate { nodeFqnAndOffset -> nodeFqnAndOffset.identifier to nodeFqnAndOffset.offset }
}

private val YAML_PROPERTY_LIKE_TOKEN_TYPES = TokenSet.create(YAMLElementTypes.KEY_VALUE_PAIR)

private fun getUnquotedKeyFromYamlKeyValue(tree: LighterAST, node: LighterASTNode): String? {
  val childIdentifier = LightTreeUtil.firstChildOfType(tree, node, YAMLTokenTypes.SCALAR_KEY)
  if (childIdentifier !is LighterASTTokenNode) return null
  return StringUtil.unquoteString(childIdentifier.text.toString())
}