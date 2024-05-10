// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.preimport

import org.jdom.Element
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.utils.MavenJDOMUtil
import java.nio.charset.Charset
import java.nio.file.Path

interface MavenStructureProjectVisitor {
  suspend fun readXml(rootProjectFile: Path): Element? {
    return MavenJDOMUtil.read(rootProjectFile, Charset.defaultCharset(), null)
  }

  fun child(aggregatorProjectFile: Path, moduleName: String): Path?
  fun map(allProjects: ArrayList<MavenProject>) {
    TODO("Not yet implemented")
  }
}
