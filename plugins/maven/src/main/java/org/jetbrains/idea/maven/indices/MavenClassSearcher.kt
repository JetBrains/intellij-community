// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.idea.maven.indices.searcher.MavenLuceneIndexer
import org.jetbrains.idea.maven.model.MavenArtifactInfo
import org.jetbrains.idea.maven.model.MavenDependencyCompletionItem
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo
import org.jetbrains.idea.maven.server.MavenServerIndexer
import java.util.Arrays
import java.util.Locale
import java.util.function.Consumer
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class MavenClassSearcher : MavenSearcher<MavenClassSearchResult>() {
  override fun searchImpl(project: Project, pattern: String, maxResult: Int): List<MavenClassSearchResult> {
    val repos = MavenIndexUtils.getAllRepositories(project)
    return MavenLuceneIndexer.getInstance().searchSync(pattern, repos, maxResult)
  }

  companion object {
    const val TERM: String = MavenServerIndexer.SEARCH_TERM_CLASS_NAMES

    fun preparePattern(pattern: String): String {
      var pattern = pattern
      pattern = pattern.lowercase(Locale.getDefault())
      if (pattern.trim { it <= ' ' }.isEmpty()) {
        return ""
      }

      val parts = StringUtil.split(pattern, ".")

      val newPattern = StringBuilder()
      for (i in 0..<parts.size - 1) {
        val each = parts[i]
        newPattern.append(each.trim { it <= ' ' })
        newPattern.append("*.")
      }

      val className = parts[parts.size - 1]
      val exactSearch = className.endsWith(" ")
      newPattern.append(className.trim { it <= ' ' })
      if (!exactSearch) newPattern.append("*")

      return newPattern.toString()
    }

    fun processResults(
      infos: Set<MavenArtifactInfo>,
      pattern: String,
      maxResult: Int,
    ): Collection<MavenClassSearchResult> {
      var pattern = pattern
      if (pattern.isEmpty() || pattern == "*") {
        pattern = "^/(.*)$"
      }
      else {
        pattern = pattern.replace(".", "/")

        val lastDot = pattern.lastIndexOf("/")
        var packagePattern = if (lastDot == -1) "" else (pattern.substring(0, lastDot) + "/")
        var classNamePattern = if (lastDot == -1) pattern else pattern.substring(lastDot + 1)

        packagePattern = packagePattern.replace("\\*".toRegex(), ".*?")
        classNamePattern = classNamePattern.replace("\\*".toRegex(), "[^/]*?")

        pattern = packagePattern + classNamePattern

        pattern = ".*?/$pattern"
        pattern = "^($pattern)$"
      }
      val p: Pattern?
      try {
        p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE or Pattern.MULTILINE)
      }
      catch (_: PatternSyntaxException) {
        return mutableListOf()
      }

      val result: MutableMap<String, MavenClassSearchResult> = HashMap()
      for (each in infos) {
        if (each.classNames == null) continue

        val matcher = p.matcher(each.classNames)
        while (matcher.find()) {
          var classFQName = matcher.group(1)
          classFQName = classFQName.replace("/", ".")
          classFQName = classFQName.removePrefix(".")

          val key = classFQName

          val classResult = result[key]
          if (classResult == null) {
            val pos = classFQName.lastIndexOf(".")
            val artifactInfo = MavenRepoArtifactInfo(
              each.groupId, each.artifactId,
              mutableListOf<String?>(each.version)
            )
            if (pos == -1) {
              result[key] = MavenClassSearchResult(artifactInfo, classFQName, "default package")
            }
            else {
              result[key] = MavenClassSearchResult(artifactInfo, classFQName.substring(pos + 1), classFQName.substring(0, pos))
            }
          }
          else {
            val versions = ContainerUtil.append<String?>(
              classResult.searchResults.getItems().map { it.version },
              each.version
            )
            val artifactInfo = MavenRepoArtifactInfo(
              each.groupId, each.artifactId,
              versions
            )
            result[key] = MavenClassSearchResult(artifactInfo, classResult.className, classResult.packageName)
          }


          if (result.size > maxResult) break
        }
      }

      result.values.forEach(Consumer {
        Arrays.sort(
          it.searchResults.getItems(),
          Comparator.comparing<MavenDependencyCompletionItem, String>(
            java.util.function.Function { obj: MavenDependencyCompletionItem? -> obj!!.version },
            VersionComparatorUtil.COMPARATOR
          )
            .reversed()
        )
      }
      )
      return result.values
    }
  }
}