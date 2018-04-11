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

private val LOG = logger<SvnBranchConfigurationNew>()

/**
 * Sorts branch locations by length descending as there could be cases when one branch location is under another.
 */
private fun sortBranchLocations(branchLocations: Collection<String>) = branchLocations.sortedByDescending { it.length }

class SvnBranchConfigurationNew {
  var trunkUrl: String = ""
  private val myBranchMap: MutableMap<String, InfoStorage<List<SvnBranchItem>>> = mutableMapOf()
  var isUserinfoInUrl: Boolean = false

  val branchUrls get() = myBranchMap.keys.map { it.removeSuffix("/") }.sorted()
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
    result.isUserinfoInUrl = isUserinfoInUrl
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

  @Throws(SvnBindException::class)
  private fun iterateUrls(listener: UrlListener) {
    if (listener.accept(trunkUrl)) {
      return
    }

    for (branchUrl in sortBranchLocations(myBranchMap.keys)) {
      val children = myBranchMap[branchUrl]!!.value
      for (child in children) {
        if (listener.accept(child.url.toDecodedString())) {
          return
        }
      }
    }
  }

  // to retrieve mappings between existing in the project working copies and their URLs
  fun getUrl2FileMappings(project: Project, root: VirtualFile) = try {
    val searcher = BranchRootSearcher(SvnVcs.getInstance(project), root)
    iterateUrls(searcher)
    searcher.branchesUnder
  }
  catch (e: SvnBindException) {
    null
  }

  fun removeBranch(url: String) {
    myBranchMap.remove(ensureEndSlash(url))
  }

  private class BranchRootSearcher constructor(vcs: SvnVcs, private val myRoot: VirtualFile) : UrlListener {
    private val myRootUrl = vcs.getInfo(myRoot.path)?.url
    // url path to file path
    val branchesUnder: MutableMap<String, String> = mutableMapOf()

    @Throws(SvnBindException::class)
    override fun accept(url: String?): Boolean {
      if (myRootUrl != null) {
        val baseDir = virtualToIoFile(myRoot)
        val baseUrl = myRootUrl.path
        val branchUrl = createUrl(url!!)

        if (isAncestor(myRootUrl, branchUrl)) {
          val file = SvnUtil.fileFromUrl(baseDir, baseUrl, branchUrl.path)
          branchesUnder[url] = file.absolutePath
        }
      }
      return false // iterate everything
    }
  }

  private interface UrlListener {
    @Throws(SvnBindException::class)
    fun accept(url: String?): Boolean
  }

  companion object {
    fun ensureEndSlash(name: String) = if (name.trim { it <= ' ' }.endsWith("/")) name else "$name/"
  }
}
