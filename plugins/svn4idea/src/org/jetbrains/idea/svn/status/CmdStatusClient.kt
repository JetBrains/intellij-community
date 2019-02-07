// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.status

import com.intellij.openapi.util.Getter
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil.*
import com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces
import com.intellij.util.containers.ContainerUtil.find
import com.intellij.util.containers.Convertor
import org.jetbrains.idea.svn.SvnUtil
import org.jetbrains.idea.svn.SvnUtil.append
import org.jetbrains.idea.svn.SvnUtil.isSvnVersioned
import org.jetbrains.idea.svn.api.*
import org.jetbrains.idea.svn.api.Target
import org.jetbrains.idea.svn.checkin.CommitInfo
import org.jetbrains.idea.svn.commandLine.*
import org.jetbrains.idea.svn.commandLine.CommandUtil.parse
import org.jetbrains.idea.svn.commandLine.CommandUtil.requireExistingParent
import org.jetbrains.idea.svn.info.Info
import org.jetbrains.idea.svn.lock.Lock
import java.io.File
import javax.xml.bind.JAXBException
import javax.xml.bind.annotation.*
import javax.xml.bind.annotation.adapters.XmlAdapter
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter

private fun putParameters(parameters: MutableList<String>,
                          path: File,
                          depth: Depth?,
                          remote: Boolean,
                          reportAll: Boolean,
                          includeIgnored: Boolean) {
  CommandUtil.put(parameters, path)
  CommandUtil.put(parameters, depth)
  CommandUtil.put(parameters, remote, "-u")
  CommandUtil.put(parameters, reportAll, "--verbose")
  CommandUtil.put(parameters, includeIgnored, "--no-ignore")
  parameters.add("--xml")
}

private fun parseResult(base: File,
                        infoBase: Info?,
                        infoProvider: Convertor<File, Info>,
                        result: String,
                        handler: StatusConsumer): Boolean {
  val externalsMap = mutableMapOf<File, Info?>()
  var hasEntries = false

  fun setUrlAndNotifyHandler(builder: Status.Builder) {
    builder.infoProvider = Getter { infoProvider.convert(builder.file) }

    val file = builder.file
    val externalsBase = find(externalsMap.keys) { isAncestor(it, file, false) }
    val baseFile = externalsBase ?: base
    val baseInfo = if (externalsBase != null) externalsMap[externalsBase] else infoBase

    if (baseInfo != null) {
      builder.url = append(baseInfo.url!!, toSystemIndependentName(getRelativePath(baseFile, file)!!))
    }
    val status = builder.build()
    if (status.`is`(StatusType.STATUS_EXTERNAL)) {
      externalsMap[status.file] = try {
        status.info
      }
      catch (e: SvnExceptionWrapper) {
        throw SvnBindException(e.cause)
      }
    }
    hasEntries = true
    handler.consume(status)
  }

  val statusRoot = parse(result, StatusRoot::class.java)
  if (statusRoot != null) {
    statusRoot.target?.entries?.forEach { entry ->
      val builder = entry.toBuilder(base)
      setUrlAndNotifyHandler(builder)
    }

    for (changeList in statusRoot.changeLists) {
      changeList.entries.forEach { entry ->
        val builder = entry.toBuilder(base)
        builder.changeListName = changeList.name
        setUrlAndNotifyHandler(builder)
      }
    }
  }

  return hasEntries
}

class CmdStatusClient : BaseSvnClient(), StatusClient {
  @Throws(SvnBindException::class)
  override fun doStatus(path: File,
                        depth: Depth,
                        remote: Boolean,
                        reportAll: Boolean,
                        includeIgnored: Boolean,
                        collectParentExternals: Boolean,
                        handler: StatusConsumer) {
    val base = requireExistingParent(path)
    val infoBase = myFactory.createInfoClient().doInfo(base, null)
    val parameters = mutableListOf<String>()

    putParameters(parameters, path, depth, remote, reportAll, includeIgnored)

    val command = execute(myVcs, Target.on(path), SvnCommandName.st, parameters, null)
    parseResult(path, base, infoBase, command, handler)
  }

  @Throws(SvnBindException::class)
  override fun doStatus(path: File, remote: Boolean): Status? {
    val status = Ref.create<Status>()
    doStatus(path, Depth.EMPTY, remote, false, false, false) { status.set(it) }
    return status.get()
  }

  @Throws(SvnBindException::class)
  private fun parseResult(path: File, base: File, infoBase: Info?, command: CommandExecutor, handler: StatusConsumer) {
    val result = command.output
    if (isEmptyOrSpaces(result)) throw SvnBindException("Status request returned nothing for command: ${command.commandText}")

    try {
      if (!parseResult(base, infoBase, createInfoGetter(), result, handler)) {
        if (!isSvnVersioned(myVcs, path)) {
          throw SvnBindException(ErrorCode.WC_NOT_WORKING_COPY, "Command - ${command.commandText}. Result - $result")
        }
        else {
          // return status indicating "NORMAL" state
          // typical output would be like
          // <status>
          // <target path="1.txt"></target>
          // </status>
          // so it does not contain any <entry> element and current parsing logic returns null

          val status = Status.Builder(path)
          status.itemStatus = StatusType.STATUS_NORMAL
          status.infoProvider = Getter { createInfoGetter().convert(path) }
          handler.consume(status.build())
        }
      }
    }
    catch (e: JAXBException) {
      // status parsing errors are logged separately as sometimes there are parsing errors connected to terminal output handling.
      // these errors primarily occur when status output is rather large.
      // and status output could be large, for instance, when working copy is locked (seems that each file is listed in status output).
      command.logCommand()
      throw SvnBindException(e)
    }
  }

  private fun createInfoGetter() = Convertor<File, Info> {
    try {
      myFactory.createInfoClient().doInfo(it, null)
    }
    catch (e: SvnBindException) {
      throw SvnExceptionWrapper(e)
    }
  }

  companion object {
    fun parseResult(base: File, result: String): Status? {
      val ref = Ref<Status?>()
      parseResult(base, null, Convertor { null }, result, StatusConsumer(ref::set))
      return ref.get()
    }
  }
}

private class StatusRevisionNumberAdapter : XmlAdapter<String, Long>() {
  override fun marshal(v: Long) = throw UnsupportedOperationException()

  override fun unmarshal(v: String) = when (v) {
    Revision.UNDEFINED.toString() -> -1L
    else -> v.toLong()
  }
}

@XmlRootElement(name = "status")
@XmlAccessorType(XmlAccessType.FIELD)
private class StatusRoot {
  var target: StatusTarget? = null

  @XmlElement(name = "changelist")
  val changeLists = mutableListOf<ChangeList>()
}

@XmlAccessorType(XmlAccessType.NONE)
private class StatusTarget {
  @XmlElement(name = "entry")
  val entries = mutableListOf<Entry>()
}

@XmlAccessorType(XmlAccessType.FIELD)
private class ChangeList {
  @XmlAttribute(required = true)
  var name: String = ""

  @XmlElement(name = "entry")
  val entries = mutableListOf<Entry>()
}

@XmlAccessorType(XmlAccessType.FIELD)
private class Entry {
  @XmlAttribute(required = true)
  var path: String = ""

  @XmlElement(name = "wc-status")
  var localStatus = WorkingCopyStatus()

  @XmlElement(name = "repos-status")
  var repositoryStatus: RepositoryStatus? = null

  fun toBuilder(base: File) = Status.Builder(SvnUtil.resolvePath(base, path)).apply {
    fileExists = file.exists()
    nodeKind = if (fileExists) NodeKind.from(file.isDirectory) else NodeKind.UNKNOWN
    if (!fileExists) fixInvalidOutputForUnversionedBase(base, this)

    itemStatus = localStatus.itemStatus
    propertyStatus = localStatus.propertyStatus
    revision = Revision.of(localStatus.revision ?: -1L)
    isWorkingCopyLocked = localStatus.isWorkingCopyLocked
    isCopied = localStatus.isCopied
    isSwitched = localStatus.isSwitched
    isTreeConflicted = localStatus.isTreeConflicted
    commitInfo = localStatus.commit
    localLock = localStatus.lock

    remoteItemStatus = repositoryStatus?.itemStatus
    remotePropertyStatus = repositoryStatus?.propertyStatus
    remoteLock = repositoryStatus?.lock
  }

  /**
   * Reproducible with svn 1.7 clients
   */
  fun fixInvalidOutputForUnversionedBase(base: File, status: Status.Builder) {
    if (base.name == path && StatusType.MISSING != status.itemStatus && StatusType.STATUS_DELETED != status.itemStatus) {
      status.fileExists = true
      status.nodeKind = NodeKind.DIR
      status.file = base
    }
  }
}

@XmlAccessorType(XmlAccessType.FIELD)
private class WorkingCopyStatus {
  @XmlAttribute(name = "item", required = true)
  var itemStatus = StatusType.STATUS_NONE

  @XmlAttribute(name = "props", required = true)
  var propertyStatus = StatusType.STATUS_NONE

  @XmlJavaTypeAdapter(StatusRevisionNumberAdapter::class)
  @XmlAttribute
  var revision: Long? = null

  @XmlAttribute(name = "wc-locked")
  var isWorkingCopyLocked = false

  @XmlAttribute(name = "copied")
  var isCopied = false

  @XmlAttribute(name = "switched")
  var isSwitched = false

  @XmlAttribute(name = "tree-conflicted")
  var isTreeConflicted = false

  var commit: CommitInfo.Builder? = null
  var lock: Lock.Builder? = null
}

@XmlAccessorType(XmlAccessType.FIELD)
private class RepositoryStatus {
  @XmlAttribute(name = "item", required = true)
  var itemStatus = StatusType.STATUS_NONE

  @XmlAttribute(name = "props", required = true)
  var propertyStatus = StatusType.STATUS_NONE

  var lock: Lock.Builder? = null
}
