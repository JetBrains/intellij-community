package com.intellij.searchEverywhereLucene.backend.providers.files

import com.intellij.openapi.vfs.VirtualFile

class LuceneFileSearchResult(val file: VirtualFile, val score: Float)