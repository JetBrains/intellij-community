// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import org.jetbrains.idea.svn.SmallMapSerializer
import org.jetbrains.idea.svn.SvnUtil.createUrl
import org.jetbrains.idea.svn.commandLine.SvnBindException
import java.io.DataInput
import java.io.DataOutput
import java.io.File
import java.io.IOException
import java.lang.String.CASE_INSENSITIVE_ORDER

class SvnLoadedBranchesStorage(private val myProject: Project) {
  private val myLock = Any()
  private var myState: SmallMapSerializer<String, Map<String, Collection<SvnBranchItem>>>? = null
  private val myFile: File

  init {
    val vcsFile = File(PathManager.getSystemPath(), "vcs")
    val file = File(vcsFile, "svn_branches")
    file.mkdirs()
    myFile = File(file, myProject.locationHash)
  }

  operator fun get(url: String) = synchronized(myLock) {
    myState?.get("")?.get(SvnBranchConfigurationNew.ensureEndSlash(url))
  }

  fun activate() = synchronized(myLock) {
    myState = SmallMapSerializer(myFile, EnumeratorStringDescriptor.INSTANCE, createExternalizer())
  }

  fun deactivate() {
    val branchLocations = mutableMapOf<String, Collection<SvnBranchItem>>()
    val branchConfigurations = SvnBranchConfigurationManager.getInstance(myProject)!!.svnBranchConfigManager.mapCopy

    for (configuration in branchConfigurations.values) {
      for ((branchLocation, branches) in configuration.branchMap) {
        branchLocations[branchLocation] = branches.value
      }
    }
    synchronized(myLock) {
      // TODO: Possibly implement optimization - do not perform save if there are no changes in branch locations and branch items
      // ensure myState.put() is called - so myState will treat itself as dirty and myState.force() will invoke real persisting
      myState!!.put("", branchLocations)
      myState!!.force()
      myState = null
    }
  }

  private fun createExternalizer() = object : DataExternalizer<Map<String, Collection<SvnBranchItem>>> {
    @Throws(IOException::class)
    override fun save(out: DataOutput, branchLocations: Map<String, Collection<SvnBranchItem>>) = with(out) {
      writeInt(branchLocations.size)
      for ((branchLocation, branches) in branchLocations.entries.sortedBy { it.key }) {
        writeUTF(branchLocation)
        writeInt(branches.size)
        for (item in branches.sortedWith(compareBy(CASE_INSENSITIVE_ORDER) { it.url.toDecodedString() })) {
          writeUTF(item.url.toDecodedString())
          writeLong(item.creationDateMillis)
          writeLong(item.revision)
        }
      }
    }

    @Throws(IOException::class)
    override fun read(`in`: DataInput) = with(`in`) {
      val branchLocations = mutableMapOf<String, Collection<SvnBranchItem>>()
      val branchLocationsSize = readInt()

      repeat(branchLocationsSize) {
        val branchLocation = readUTF()
        val branchesSize = readInt()
        val branches = mutableListOf<SvnBranchItem>()

        repeat(branchesSize) {
          val urlValue = readUTF()
          val creationDateMillis = readLong()
          val revision = readLong()
          val url = try {
            createUrl(urlValue, false)
          }
          catch (e: SvnBindException) {
            throw IOException("Could not parse url $urlValue", e)
          }

          branches.add(SvnBranchItem(url, creationDateMillis, revision))
        }

        branchLocations[branchLocation] = branches
      }

      branchLocations
    }
  }
}
