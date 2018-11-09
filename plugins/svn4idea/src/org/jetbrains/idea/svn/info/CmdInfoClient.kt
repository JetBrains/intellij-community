// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.info

import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces
import com.intellij.openapi.vfs.CharsetToolkit
import org.jetbrains.idea.svn.api.BaseSvnClient
import org.jetbrains.idea.svn.api.Depth
import org.jetbrains.idea.svn.api.Revision
import org.jetbrains.idea.svn.api.Target
import org.jetbrains.idea.svn.commandLine.*
import org.jetbrains.idea.svn.commandLine.CommandUtil.requireExistingParent
import org.xml.sax.SAXException
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

private val LOG = logger<CmdInfoClient>()

private fun parseResult(base: File?, result: String?): Info? {
  val ref = Ref<Info?>()
  parseResult(InfoConsumer(ref::set), base, result)
  return ref.get()
}

private fun parseResult(handler: InfoConsumer, base: File?, result: String?) {
  if (isEmptyOrSpaces(result)) return

  val infoHandler = SvnInfoHandler(base) {
    try {
      handler.consume(it)
    }
    catch (e: SvnBindException) {
      throw SvnExceptionWrapper(e)
    }
  }

  parseResult(result!!, infoHandler)
}

private fun parseResult(result: String, handler: SvnInfoHandler) {
  try {
    SAXParserFactory.newInstance().newSAXParser().parse(
      ByteArrayInputStream(result.trim { it <= ' ' }.toByteArray(CharsetToolkit.UTF8_CHARSET)), handler)
  }
  catch (e: SvnExceptionWrapper) {
    LOG.info("info output $result")
    throw SvnBindException(e.cause)
  }
  catch (e: IOException) {
    LOG.info("info output $result")
    throw SvnBindException(e)
  }
  catch (e: SAXException) {
    LOG.info("info output $result")
    throw SvnBindException(e)
  }
  catch (e: ParserConfigurationException) {
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
}