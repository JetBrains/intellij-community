// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.python.community.impl.huggingFace.HuggingFaceConstants
import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceApi
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceDatasetsCache
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceModelsCache

private class HuggingFaceProjectStartupActivity : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    HuggingFaceApi.fillCacheWithBasicApiData(HuggingFaceEntityKind.MODEL,
                                             HuggingFaceModelsCache,
                                             HuggingFaceConstants.MAX_MODELS_IN_CACHE,
                                             project)
    HuggingFaceApi.fillCacheWithBasicApiData(HuggingFaceEntityKind.DATASET,
                                             HuggingFaceDatasetsCache,
                                             HuggingFaceConstants.MAX_DATASETS_IN_CACHE,
                                             project)
  }
}
