// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.psi.PsiFileFactory
import com.intellij.util.containers.FList
import com.intellij.util.containers.MultiMap
import org.jetbrains.idea.maven.model.MavenArtifactNode
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter

class ShowConflictDependencies : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val manager = MavenProjectsManager.getInstance(e.project)!!

    val mavenProject = MavenActionUtil.getMavenProject(e.dataContext)!!

    val fileName = mavenProject.getMavenId().getArtifactId()!! + "-conflicts.txt"

    val stringWriter = StringWriter()
    PrintWriter(stringWriter).use { pw ->

      val duplicates = MultiMap.create<Pair<String, String>, Pair<MavenArtifactNode, FList<MavenArtifactNode>>>() //ArrayList<Pair<MavenArtifactNode, List<MavenArtifactNode>>>()

      fun walk(parents: FList<MavenArtifactNode>, indent: String, node: MavenArtifactNode) {
        pw.println(indent + node.artifact + " " + node.state)
        //        if(node.state == MavenArtifactState.CONFLICT)
        //          duplicates.add(node to parents)

        duplicates.putValue(node.artifact.groupId to node.artifact.artifactId, node to parents)

        for (dependency in node.dependencies) {
          walk(parents.prepend(node), "$indent  |", dependency)
        }


      }

      for (dependency in mavenProject.dependencyTree) {
        walk(FList.emptyList(), "", dependency)
      }

      pw.println("=======================")
      pw.println("DUPLICATES:")

      //      duplicates.sortWith(Comparator.comparing<Pair<MavenArtifactNode, List<MavenArtifactNode>>, String>{it.first .toString()})

      for ((groupAndArtifact, entries) in duplicates.entrySet()) {
        //        pw.println(generateSequence(duplicate) { duplicate.parent  }.take(20).joinToString(" -> "))
        //        pw.println( duplicate.toString() + " -> " + parent.joinToString(" -> ") { it.toString() } )
        pw.println(groupAndArtifact)
        for ((entry, parents) in entries) {
          pw.println("  " + entry.toString() + " -> " + parents.joinToString(" -> ") { it.toString() })
        }


      }

    }


    val file1 = PsiFileFactory.getInstance(e.project).createFileFromText(fileName, PlainTextLanguage.INSTANCE, stringWriter.buffer)
    try {
      file1.virtualFile.isWritable = false
    }
    catch (e: IOException) {
    }


    file1.navigate(true)

  }
}