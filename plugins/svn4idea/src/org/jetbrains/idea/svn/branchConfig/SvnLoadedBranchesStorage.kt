// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import org.jetbrains.idea.svn.SmallMapSerializer
import org.jetbrains.idea.svn.api.Url
import org.jetbrains.idea.svn.branchConfig.UrlDescriptor.Companion.DECODED_URL_DESCRIPTOR
import org.jetbrains.idea.svn.branchConfig.UrlDescriptor.Companion.ENCODED_URL_DESCRIPTOR
import java.io.DataInput
import java.io.DataOutput
import java.io.File
import java.io.IOException
import java.lang.String.CASE_INSENSITIVE_ORDER

class SvnLoadedBranchesStorage(private val myProject: Project) {
  private val myLock = Any()
  private var myState: SmallMapSerializer<String, Map<Url, Collection<SvnBranchItem>>>? = null
  private val myFile: File

  init {
    val vcsFile = File(PathManager.getSystemPath(), "vcs")
    val file = File(vcsFile, "svn_branches")
    file.mkdirs()
    myFile = File(file, myProject.locationHash)
  }

  operator fun get(url: Url): Collection<SvnBranchItem>? = synchronized(myLock) {
    myState?.get("")?.get(url)
  }

  fun activate(): Unit = synchronized(myLock) {
    myState = SmallMapSerializer(myFile, EnumeratorStringDescriptor.INSTANCE, createExternalizer())
  }

  fun deactivate() {
    val branchLocations = mutableMapOf<Url, Collection<SvnBranchItem>>()
    val branchConfigurations = SvnBranchConfigurationManager.getInstance(myProject).svnBranchConfigManager.mapCopy

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

  private fun createExternalizer() = object : DataExternalizer<Map<Url, Collection<SvnBranchItem>>> {
    @Throws(IOException::class)
    override fun save(out: DataOutput, branchLocations: Map<Url, Collection<SvnBranchItem>>) = with(out) {
      writeInt(branchLocations.size)
      for ((branchLocation, branches) in branchLocations.entries.sortedBy { it.key.toDecodedString() }) {
        ENCODED_URL_DESCRIPTOR.save(this, branchLocation)
        writeInt(branches.size)
        for (item in branches.sortedWith(compareBy(CASE_INSENSITIVE_ORDER) { it.url.toDecodedString() })) {
          DECODED_URL_DESCRIPTOR.save(this, item.url)
          writeLong(item.creationDateMillis)
          writeLong(item.revision)
        }
      }
    }

    @Throws(IOException::class)
    override fun read(`in`: DataInput) = with(`in`) {
      val branchLocations = mutableMapOf<Url, Collection<SvnBranchItem>>()
      val branchLocationsSize = readInt()

      repeat(branchLocationsSize) {
        val branchLocation = ENCODED_URL_DESCRIPTOR.read(this)
        val branchesSize = readInt()
        val branches = mutableListOf<SvnBranchItem>()

        repeat(branchesSize) {
          val url = DECODED_URL_DESCRIPTOR.read(this)
          val creationDateMillis = readLong()
          val revision = readLong()

          branches.add(SvnBranchItem(url, creationDateMillis, revision))
        }

        branchLocations[branchLocation] = branches
      }

      branchLocations
    }
  }
}
