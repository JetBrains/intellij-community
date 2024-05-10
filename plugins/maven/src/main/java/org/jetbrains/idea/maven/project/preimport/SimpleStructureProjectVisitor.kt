// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.preimport

import org.jdom.Element
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.utils.MavenJDOMUtil
import java.nio.charset.Charset
import java.nio.file.Path

class SimpleStructureProjectVisitor : MavenStructureProjectVisitor {
  override suspend fun readXml(rootProjectFile: Path): Element? {
    return MavenJDOMUtil.read(rootProjectFile, Charset.defaultCharset(), null)
  }

  override fun child(aggregatorProjectFile: Path, moduleName: String): Path? {
    val resolve = aggregatorProjectFile.parent.resolve(moduleName)
    var file = resolve.toFile()
    if (file.isDirectory) {
      file = file.resolve("pom.xml")
    }
    if (file.isFile) return file.toPath();
    return null
  }

  override fun map(allProjects: ArrayList<MavenProject>) {
  }
}
