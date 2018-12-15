// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.info

import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import org.jetbrains.idea.svn.SvnUtil.createUrl
import org.jetbrains.idea.svn.SvnUtil.resolvePath
import org.jetbrains.idea.svn.api.*
import org.jetbrains.idea.svn.api.Target
import org.jetbrains.idea.svn.checkin.CommitInfo
import org.jetbrains.idea.svn.commandLine.CommandUtil
import org.jetbrains.idea.svn.commandLine.CommandUtil.parse
import org.jetbrains.idea.svn.commandLine.CommandUtil.requireExistingParent
import org.jetbrains.idea.svn.commandLine.LineCommandAdapter
import org.jetbrains.idea.svn.commandLine.SvnBindException
import org.jetbrains.idea.svn.commandLine.SvnCommandName
import org.jetbrains.idea.svn.conflict.TreeConflictDescription
import org.jetbrains.idea.svn.lock.Lock
import java.io.File
import javax.xml.bind.JAXBException
import javax.xml.bind.annotation.*
import javax.xml.bind.annotation.adapters.XmlAdapter
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter

private val LOG = logger<CmdInfoClient>()

private fun parseResult(handler: InfoConsumer, base: File?, result: String) {
  try {
    val infoRoot = parse(result, InfoRoot::class.java)

    if (infoRoot != null) {
      for (entry in infoRoot.entries) {
        val file = base?.let { resolvePath(it, entry.path) }
        val url = entry.url?.let { createUrl(it) }
        val repositoryRootUrl = entry.repository?.root?.let { createUrl(it) }
        val copyFromUrl = entry.workingCopyInfo?.copyFromUrl?.let { createUrl(it) }

        val info = Info(file, url, Revision.of(entry.revisionNumber ?: -1L), entry.nodeKind, repositoryRootUrl, entry.repository?.uuid,
                        entry.commit?.build(), entry.workingCopyInfo?.schedule, entry.workingCopyInfo?.depth, copyFromUrl,
                        Revision.of(entry.workingCopyInfo?.copyFromRevision ?: -1L), entry.lock?.build(), entry.conflict?.previousBaseFile,
                        entry.conflict?.currentBaseFile, entry.conflict?.previousWorkingCopyFile, entry.treeConflict?.build(base!!))
        handler.consume(info)
      }
    }
  }
  catch (e: JAXBException) {
    LOG.info("info output $result")
    throw SvnBindException(e)
  }
}

private fun buildParameters(target: Target, revision: Revision?): List<String> = mutableListOf<String>().apply {
  CommandUtil.put(this, Depth.EMPTY)
  CommandUtil.put(this, revision)
  CommandUtil.put(this, target)
  add("--xml")
}

class CmdInfoClient : BaseSvnClient(), InfoClient {
  private fun execute(parameters: List<String>, path: File): String {
    // workaround: separately capture command output - used in exception handling logic to overcome svn 1.8 issue (see below)
    val output = ProcessOutput()
    val listener = object : LineCommandAdapter() {
      override fun onLineAvailable(line: String, outputType: Key<*>) {
        if (outputType === ProcessOutputTypes.STDOUT) {
          output.appendStdout(line)
        }
      }
    }

    return try {
      execute(myVcs, Target.on(path), SvnCommandName.info, parameters, listener).output
    }
    catch (e: SvnBindException) {
      val text = e.message
      when {
        // if "svn info" is executed for several files at once, then this warning could be printed only for some files, but info for other
        // files should be parsed from output
        "W155010" in text -> output.stdout
        // TODO: Seems not reproducible in 1.8.4
        // "E155007: '' is not a working copy"
        // Workaround: in subversion 1.8 "svn info" on a working copy root outputs such error for parent folder, if there are files with
        // conflicts. But the requested info is still in the output except root closing tag.
        "is not a working copy" in text && !output.stdout.isEmpty() -> "${output.stdout}</info>"
        else -> throw e
      }
    }
  }

  @Throws(SvnBindException::class)
  override fun doInfo(path: File, revision: Revision?): Info? {
    val base = requireExistingParent(path)
    return parseResult(base, execute(buildParameters(Target.on(path), revision), path))
  }

  @Throws(SvnBindException::class)
  override fun doInfo(target: Target, revision: Revision?): Info? {
    assertUrl(target)

    val command = execute(myVcs, target, SvnCommandName.info, buildParameters(target, revision), null)
    return parseResult(null, command.output)
  }

  @Throws(SvnBindException::class)
  override fun doInfo(paths: Collection<File>, handler: InfoConsumer?) {
    val firstPath = paths.firstOrNull()
    if (firstPath != null) {
      val base = requireExistingParent(firstPath)
      val parameters = mutableListOf<String>().apply {
        paths.forEach { CommandUtil.put(this, it) }
        add("--xml")
      }

      // Currently do not handle exceptions here like in SvnVcs.handleInfoException - just continue with parsing in case of warnings for
      // some of the requested items
      val result = execute(parameters, base)
      if (handler != null) {
        parseResult(handler, base, result)
      }
    }
  }

  companion object {
    fun parseResult(base: File?, result: String): Info? {
      val ref = Ref<Info?>()
      parseResult(InfoConsumer(ref::set), base, result)
      return ref.get()
    }
  }
}

private class InfoRevisionNumberAdapter : XmlAdapter<String, Long>() {
  override fun marshal(v: Long) = throw UnsupportedOperationException()

  override fun unmarshal(v: String) = when (v) {
    "Resource is not under version control." -> -1L
    else -> v.toLong()
  }
}

@XmlRootElement(name = "info")
@XmlAccessorType(XmlAccessType.NONE)
private class InfoRoot {
  @XmlElement(name = "entry")
  val entries = mutableListOf<Entry>()
}

@XmlAccessorType(XmlAccessType.FIELD)
private class Entry {
  @XmlAttribute(required = true)
  var path = ""

  @XmlAttribute(name = "kind", required = true)
  var nodeKind = NodeKind.UNKNOWN

  @XmlJavaTypeAdapter(InfoRevisionNumberAdapter::class)
  @XmlAttribute(name = "revision", required = true)
  var revisionNumber: Long? = null

  var url: String? = null
  var repository: Repository? = null

  @XmlElement(name = "wc-info")
  var workingCopyInfo: WorkingCopyInfo? = null

  var commit: CommitInfo.Builder? = null
  var lock: Lock.Builder? = null
  var conflict: Conflict? = null

  @XmlElement(name = "tree-conflict")
  var treeConflict: TreeConflictDescription.Builder? = null
}

@XmlAccessorType(XmlAccessType.FIELD)
private class Repository {
  var root: String? = null
  var uuid: String? = null
}

@XmlAccessorType(XmlAccessType.FIELD)
private class WorkingCopyInfo {
  var schedule: String? = null
  var depth: Depth = Depth.UNKNOWN

  @XmlElement(name = "copy-from-url")
  var copyFromUrl: String? = null

  @XmlElement(name = "copy-from-rev")
  var copyFromRevision = -1L
}

@XmlAccessorType(XmlAccessType.NONE)
private class Conflict {
  @XmlElement(name = "prev-base-file", required = true)
  var previousBaseFile = ""

  @XmlElement(name = "prev-wc-file")
  var previousWorkingCopyFile: String? = null

  @XmlElement(name = "cur-base-file", required = true)
  var currentBaseFile = ""
}