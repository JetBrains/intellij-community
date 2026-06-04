package com.intellij.mermaid.lang

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.vfs.VirtualFile

fun FileType.isMermaidType(): Boolean {
  return this == MermaidFileType
}

fun VirtualFile.isMermaidFile(): Boolean {
  return FileTypeRegistry.getInstance().isFileOfType(this, MermaidFileType)
}
