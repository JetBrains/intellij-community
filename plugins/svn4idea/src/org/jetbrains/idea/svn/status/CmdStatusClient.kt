// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.status

import com.intellij.openapi.util.Getter
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.containers.ContainerUtil.find
import com.intellij.util.containers.ContainerUtil.newHashMap
import com.intellij.util.containers.Convertor
import org.jetbrains.idea.svn.SvnUtil.append
import org.jetbrains.idea.svn.SvnUtil.isSvnVersioned
import org.jetbrains.idea.svn.api.BaseSvnClient
import org.jetbrains.idea.svn.api.Depth
import org.jetbrains.idea.svn.api.ErrorCode
import org.jetbrains.idea.svn.api.Target
import org.jetbrains.idea.svn.commandLine.*
import org.jetbrains.idea.svn.commandLine.CommandUtil.requireExistingParent
import org.jetbrains.idea.svn.info.Info
import org.xml.sax.SAXException
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.function.Supplier
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory
import kotlin.collections.set

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
    parseResult(path, handler, base, infoBase, command)
  }

  @Throws(SvnBindException::class)
  override fun doStatus(path: File, remote: Boolean): Status? {
    val status = Ref.create<Status>()
    doStatus(path, Depth.EMPTY, remote, false, false, false) { status.set(it) }
    return status.get()
  }

  @Throws(SvnBindException::class)
  private fun parseResult(path: File, handler: StatusConsumer, base: File, infoBase: Info?, command: CommandExecutor) {
    val result = command.output

    if (StringUtil.isEmptyOrSpaces(result)) {
      throw SvnBindException("Status request returned nothing for command: " + command.commandText)
    }

    try {
      val parsingHandler = Ref.create<SvnStatusHandler>()
      parsingHandler.set(createStatusHandler(handler, base, infoBase, Supplier { parsingHandler.get().pending }))
      val parser = SAXParserFactory.newInstance().newSAXParser()
      parser.parse(ByteArrayInputStream(result.trim { it <= ' ' }.toByteArray(CharsetToolkit.UTF8_CHARSET)), parsingHandler.get())
      if (!parsingHandler.get().isAnythingReported) {
        if (!isSvnVersioned(myVcs, path)) {
          throw SvnBindException(ErrorCode.WC_NOT_WORKING_COPY, "Command - " + command.commandText + ". Result - " + result)
        }
        else {
          // return status indicating "NORMAL" state
          // typical output would be like
          // <status>
          // <target path="1.txt"></target>
          // </status>
          // so it does not contain any <entry> element and current parsing logic returns null

          val status = Status.Builder()
          status.file = path
          status.itemStatus = StatusType.STATUS_NORMAL
          status.infoProvider = Getter { createInfoGetter().convert(path) }
          handler.consume(status.build())
        }
      }
    }
    catch (e: SvnExceptionWrapper) {
      throw SvnBindException(e.cause)
    }
    catch (e: IOException) {
      throw SvnBindException(e)
    }
    catch (e: ParserConfigurationException) {
      throw SvnBindException(e)
    }
    catch (e: SAXException) {
      // status parsing errors are logged separately as sometimes there are parsing errors connected to terminal output handling.
      // these errors primarily occur when status output is rather large.
      // and status output could be large, for instance, when working copy is locked (seems that each file is listed in status output).
      command.logCommand()
      throw SvnBindException(e)
    }

  }

  private fun createStatusHandler(handler: StatusConsumer,
                                  base: File,
                                  infoBase: Info?,
                                  statusSupplier: Supplier<Status.Builder>): SvnStatusHandler {
    val callback = createStatusCallback(handler, base, infoBase, statusSupplier)

    return SvnStatusHandler(callback, base, createInfoGetter())
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
    @JvmStatic
    fun createStatusCallback(handler: StatusConsumer,
                             base: File,
                             infoBase: Info?,
                             statusSupplier: Supplier<Status.Builder>): SvnStatusHandler.ExternalDataCallback {
      val externalsMap = newHashMap<File, Info?>()
      val changelistName = Ref.create<String>()

      return object : SvnStatusHandler.ExternalDataCallback {
        override fun switchPath() {
          val pending = statusSupplier.get()
          pending.changeListName = changelistName.get()
          try {
            val pendingFile = pending.file
            val externalsBase = find(externalsMap.keys) { isAncestor(it, pendingFile!!, false) }
            val baseFile = externalsBase ?: base
            val baseInfo = if (externalsBase != null) externalsMap[externalsBase] else infoBase

            if (baseInfo != null) {
              pending.url = append(baseInfo.url!!, toSystemIndependentName(getRelativePath(baseFile, pendingFile)!!))
            }
            val status = pending.build()
            if (status.`is`(StatusType.STATUS_EXTERNAL)) {
              externalsMap[pending.file] = status.info
            }
            handler.consume(status)
          }
          catch (e: SvnBindException) {
            throw SvnExceptionWrapper(e)
          }
        }

        override fun switchChangeList(newList: String) = changelistName.set(newList)
      }
    }
  }
}
