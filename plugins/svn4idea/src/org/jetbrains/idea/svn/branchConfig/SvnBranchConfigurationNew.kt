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
private fun sortBranchLocations(branchLocations: Collection<Url>) = branchLocations.sortedByDescending { it.toDecodedString().length }

class SvnBranchConfigurationNew {
  var trunk: Url? = null

  private val myBranchMap: MutableMap<Url, InfoStorage<List<SvnBranchItem>>> = mutableMapOf()
  var isUserInfoInUrl: Boolean = false

  val branchLocations: List<Url> get() = myBranchMap.keys.sortedBy { it.toDecodedString() }
  val branchMap: MutableMap<Url, InfoStorage<List<SvnBranchItem>>> get() = myBranchMap

  fun addBranches(branchLocation: Url, items: InfoStorage<List<SvnBranchItem>>) {
    val current = myBranchMap[branchLocation]
    if (current != null) {
      LOG.info("Branches list not added for : '${branchLocation.toDecodedString()}; this branch parent URL is already present.")
      return
    }
    myBranchMap[branchLocation] = items
  }

  fun updateBranch(branchLocation: Url, items: InfoStorage<List<SvnBranchItem>>) {
    val current = myBranchMap[branchLocation]
    if (current == null) {
      LOG.info("Branches list not updated for : '${branchLocation.toDecodedString()}; since config has changed.")
      return
    }
    current.accept(items)
  }

  fun getBranches(branchLocation: Url): List<SvnBranchItem> = myBranchMap[branchLocation]?.value ?: emptyList()

  fun copy(): SvnBranchConfigurationNew {
    val result = SvnBranchConfigurationNew()
    result.isUserInfoInUrl = isUserInfoInUrl
    result.trunk = trunk
    for ((key, infoStorage) in myBranchMap) {
      result.myBranchMap[key] = InfoStorage(infoStorage.value.toList(), infoStorage.infoReliability)
    }
    return result
  }

  private fun getBaseUrl(url: Url): Url? {
    val trunk = trunk
    if (trunk != null && isAncestor(trunk, url)) {
      return trunk
    }
    for (branchUrl in sortBranchLocations(myBranchMap.keys)) {
      if (isAncestor(branchUrl, url)) {
        val relativePath = SvnUtil.getRelativeUrl(branchUrl, url)
        return branchUrl.appendPath(relativePath.substringBefore("/"), false)
      }
    }
    return null
  }

  @Deprecated("use getBaseName(Url)")
  fun getBaseName(url: String): String? = getBaseName(createUrl(url, false))

  fun getBaseName(url: Url): String? = getBaseUrl(url)?.tail

  fun getRelativeUrl(url: Url): String? = getBaseUrl(url)?.let { SvnUtil.getRelativeUrl(it, url) }

  @Throws(SvnBindException::class)
  fun getWorkingBranch(url: Url): Url? = getBaseUrl(url)

  // to retrieve mappings between existing in the project working copies and their URLs
  fun getUrl2FileMappings(project: Project, root: VirtualFile): Map<Url, File> {
    val rootUrl = SvnVcs.getInstance(project).getInfo(root)?.url ?: return emptyMap()
    val baseDir = virtualToIoFile(root)
    val result = mutableMapOf<Url, File>()

    for (url in allBranches) {
      if (isAncestor(rootUrl, url)) {
        result[url] = SvnUtil.fileFromUrl(baseDir, rootUrl.path, url.path)
      }
    }

    return result
  }

  private val allBranches
    get() = sequenceOf(
      trunk).filterNotNull() + myBranchMap.entries.sortedByDescending { it.key.toDecodedString().length }.asSequence().map { it.value.value }.flatten().map { it.url }

  fun removeBranch(url: Url) {
    myBranchMap.remove(url)
  }
}
