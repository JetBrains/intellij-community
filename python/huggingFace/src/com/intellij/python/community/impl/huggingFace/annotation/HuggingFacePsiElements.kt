// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.annotation

import com.intellij.ide.BrowserUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceURLProvider
import com.intellij.openapi.util.NlsSafe
import java.net.URL
import com.intellij.openapi.util.Key
import com.jetbrains.python.spellchecker.PythonSpellcheckerStrategy.PY_STRING_SPELLCHECK_IGNORE_KEY

val HUGGING_FACE_ENTITY_NAME_KEY = Key.create<Boolean>("HUGGING_FACE_ENTITY_NAME_KEY")


abstract class HuggingFaceEntityPsiElement(
  private val parent: PsiElement,
  private val entityName: String,
  private val getEntityLink: (String) -> URL
) : FakePsiElement() {
  init {
    parent.putUserData(PY_STRING_SPELLCHECK_IGNORE_KEY, true)
    parent.putUserData(HUGGING_FACE_ENTITY_NAME_KEY, true)
  }

  override fun getParent(): PsiElement = parent

  override fun navigate(requestFocus: Boolean) {
    BrowserUtil.browse(getEntityLink(entityName))
  }

  @NlsSafe
  fun stringValue(): String = entityName
}

class HuggingFaceModelPsiElement( parent: PsiElement, modelName: String
) : HuggingFaceEntityPsiElement(parent, modelName, HuggingFaceURLProvider::getModelCardLink)

class HuggingFaceDatasetPsiElement(parent: PsiElement, datasetName: String
) : HuggingFaceEntityPsiElement(parent, datasetName, HuggingFaceURLProvider::getDatasetCardLink)
