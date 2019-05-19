// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.config

import com.intellij.openapi.fileTypes.FileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeConsumer
import com.intellij.openapi.fileTypes.FileTypeFactory
import com.intellij.openapi.util.Comparing

class MavenConfigFileTypeFactory : FileTypeFactory() {
  override fun createFileTypes(consumer: FileTypeConsumer) {
    consumer.consume(MavenConfigFileType.INSTANCE, object : FileNameMatcher {
      override fun acceptsCharSequence(fileName: CharSequence): Boolean {
        return Comparing.equal("maven.config", fileName)
      }

      override fun getPresentableString(): String {
        return "Maven configuration file"
      }
    })
  }
}
