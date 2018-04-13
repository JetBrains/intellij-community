// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.svn.SvnUtil
import org.jetbrains.idea.svn.SvnUtil.createUrl
import org.jetbrains.idea.svn.SvnUtil.isAncestor
import org.jetbrains.idea.svn.SvnVcs
import org.jetbrains.idea.svn.api.Url
import org.jetbrains.idea.svn.commandLine.SvnBindException
import java.io.File

private val LOG = logger<SvnBranchConfigurationNew>()

/**
 * Sorts branch locations by length descending as there could be cases when one branch location is under another.
 */
private fun sortBranchLocations(branchLocations: Collection<String>) = branchLocations.sortedByDescending { it.length }

class SvnBranchConfigurationNew {
  var trunkUrl: String = ""
  private val myBranchMap: MutableMap<String, InfoStorage<List<SvnBranchItem>>> = mutableMapOf()
  var isUserInfoInUrl: Boolean = false

  val branchUrls get() = myBranchMap.keys.map { it.removeSuffix("/") }.sorted()
  val branchLocations
    get() = branchUrls.mapNotNull {
      try {
        createUrl(it)
      }
      catch (e: SvnBindException) {
        LOG.info("Could not parse url $it", e)
        null
      }
    }
  val branchMap get() = myBranchMap

  fun addBranches(branchParentName: String, items: InfoStorage<List<SvnBranchItem>>) {
    val branchLocation = ensureEndSlash(branchParentName)
    val current = myBranchMap[branchLocation]
    if (current != null) {
      LOG.info("Branches list not added for : '$branchLocation; this branch parent URL is already present.")
      return
    }
    myBranchMap[branchLocation] = items
  }

  fun updateBranch(branchParentName: String, items: InfoStorage<List<SvnBranchItem>>) {
    val branchLocation = ensureEndSlash(branchParentName)
    val current = myBranchMap[branchLocation]
    if (current == null) {
      LOG.info("Branches list not updated for : '$branchLocation; since config has changed.")
      return
    }
    current.accept(items)
  }

  fun getBranches(url: String) = myBranchMap[ensureEndSlash(url)]?.value ?: emptyList()

  fun copy(): SvnBranchConfigurationNew {
    val result = SvnBranchConfigurationNew()
    result.isUserInfoInUrl = isUserInfoInUrl
    result.trunkUrl = trunkUrl
    for ((key, infoStorage) in myBranchMap) {
      result.myBranchMap[key] = InfoStorage(infoStorage.value.toList(), infoStorage.infoReliability)
    }
    return result
  }

  private fun getBaseUrl(url: String): String? {
    if (Url.isAncestor(trunkUrl, url)) {
      return trunkUrl.removeSuffix("/")
    }
    for (branchUrl in sortBranchLocations(myBranchMap.keys)) {
      if (Url.isAncestor(branchUrl, url)) {
        val relativePath = Url.getRelative(branchUrl, url)
        return (branchUrl + relativePath!!.substringBefore("/")).removeSuffix("/")
      }
    }
    return null
  }

  fun getBaseName(url: String) = getBaseUrl(url)?.let { Url.tail(it) }

  fun getRelativeUrl(url: String) = getBaseUrl(url)?.let { url.substring(it.length) }

  @Throws(SvnBindException::class)
  fun getWorkingBranch(someUrl: Url) = getBaseUrl(someUrl.toString())?.let(::createUrl)

  // to retrieve mappings between existing in the project working copies and their URLs
  fun getUrl2FileMappings(project: Project, root: VirtualFile): Map<Url, File> {
    try {
      val rootUrl = SvnVcs.getInstance(project).getInfo(root)?.url ?: return emptyMap()
      val baseDir = virtualToIoFile(root)
      val result = mutableMapOf<Url, File>()

      for (url in allBranches) {
        val branchUrl = createUrl(url)

        if (isAncestor(rootUrl, branchUrl)) {
          result[branchUrl] = SvnUtil.fileFromUrl(baseDir, rootUrl.path, branchUrl.path)
        }
      }
      return result
    }
    catch (e: SvnBindException) {
      return emptyMap()
    }
  }

  private val allBranches
    get() = sequenceOf(
      trunkUrl) + myBranchMap.entries.sortedByDescending { it.key.length }.asSequence().map { it.value.value }.flatten().map { it.url.toDecodedString() }

  fun removeBranch(url: String) {
    myBranchMap.remove(ensureEndSlash(url))
  }

  companion object {
    fun ensureEndSlash(name: String) = if (name.trim { it <= ' ' }.endsWith("/")) name else "$name/"
  }
}
