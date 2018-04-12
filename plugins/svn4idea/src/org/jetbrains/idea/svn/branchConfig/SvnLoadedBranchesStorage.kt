// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import org.jetbrains.idea.svn.SmallMapSerializer
import org.jetbrains.idea.svn.SvnUtil.createUrl
import org.jetbrains.idea.svn.api.Url
import org.jetbrains.idea.svn.commandLine.SvnBindException
import java.io.DataInput
import java.io.DataOutput
import java.io.File
import java.io.IOException
import java.lang.String.CASE_INSENSITIVE_ORDER
import java.util.*

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

  operator fun get(url: String): Collection<SvnBranchItem>? {
    synchronized(myLock) {
      if (myState == null) return null
      val map = myState!!.get("")
      return if (map == null) null else map[SvnBranchConfigurationNew.ensureEndSlash(url)]
    }
  }

  fun activate() {
    synchronized(myLock) {
      myState = SmallMapSerializer(myFile, EnumeratorStringDescriptor.INSTANCE, createExternalizer())
    }
  }


  fun deactivate() {
    val branchLocationToBranchItemsMap = mutableMapOf<String, Collection<SvnBranchItem>>()
    val manager = SvnBranchConfigurationManager.getInstance(myProject)
    val mapCopy = manager!!.svnBranchConfigManager.mapCopy
    for ((_, configuration) in mapCopy) {
      val branchMap = configuration.branchMap
      for ((branchLocation, branches) in branchMap) {
        branchLocationToBranchItemsMap[branchLocation] = branches.value
      }
    }
    synchronized(myLock) {
      // TODO: Possibly implement optimization - do not perform save if there are no changes in branch locations and branch items
      // ensure myState.put() is called - so myState will treat itself as dirty and myState.force() will invoke real persisting
      myState!!.put("", branchLocationToBranchItemsMap)
      myState!!.force()
      myState = null
    }
  }

  private fun createExternalizer() = object : DataExternalizer<Map<String, Collection<SvnBranchItem>>> {
    @Throws(IOException::class)
    override fun save(out: DataOutput, value: Map<String, Collection<SvnBranchItem>>) {
      out.writeInt(value.size)
      val keys = ArrayList(value.keys)
      Collections.sort(keys)
      for (key in keys) {
        out.writeUTF(key)
        val list = ArrayList(value[key])
        Collections.sort(list, compareBy(CASE_INSENSITIVE_ORDER) { it.url.toDecodedString() })
        out.writeInt(list.size)
        for (item in list) {
          out.writeUTF(item.url.toDecodedString())
          out.writeLong(item.creationDateMillis)
          out.writeLong(item.revision)
        }
      }
    }

    @Throws(IOException::class)
    override fun read(`in`: DataInput): Map<String, Collection<SvnBranchItem>> {
      val map = HashMap<String, Collection<SvnBranchItem>>()
      val mapSize = `in`.readInt()
      for (i in 0 until mapSize) {
        val key = `in`.readUTF()
        val size = `in`.readInt()
        val list = ArrayList<SvnBranchItem>(size)
        for (j in 0 until size) {
          val urlValue = `in`.readUTF()
          val creation = `in`.readLong()
          val revision = `in`.readLong()
          val url: Url
          try {
            url = createUrl(urlValue, false)
          }
          catch (e: SvnBindException) {
            throw IOException("Could not parse url $urlValue", e)
          }

          list.add(SvnBranchItem(url, creation, revision))
        }
        map[key] = list
      }
      return map
    }
  }
}
