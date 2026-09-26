package com.intellij.ide.starter

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.report.DetailsOnCI
import com.intellij.ide.starter.runner.IDEReportingData
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.io.ByteArrayOutputStream
import java.io.PrintStream

private const val TEST_METADATA_MESSAGE_PREFIX = "##teamcity[testMetadata "
private const val ARTIFACTS_URL_PREFIX = "https://ci/artifacts#/"
private val METADATA_NAME = Regex("""\bname='([^']*)'""")
private val METADATA_VALUE = Regex("""\bvalue='([^']*)'""")

/** A `testMetadata` service message a launch reported: what it is called on the test, and what it points at. */
internal data class ReportedMetadata(val name: String, val value: String)

/** The link a launch publishing under [artifactPath] is expected to report. */
internal fun linkToArtifacts(artifactPath: String): String = ARTIFACTS_URL_PREFIX + artifactPath

/**
 * The `testMetadata` messages reported while [body] runs for launches publishing under [artifactsUnder], in the order
 * they were reported. Launches of other tests are given no link to report at all, so that whatever they left listening
 * for a test cannot report into this one.
 *
 * Output is held back only for as long as [body] runs, and everything but the messages returned here is printed
 * afterwards: a message this assertion keeps would otherwise reach the test runner of the run it is part of, which
 * would attach the links made up here to the test making them up.
 */
internal fun testMetadataReportedWhile(artifactsUnder: String, body: () -> Unit): List<ReportedMetadata> {
  val originalDi = di
  val originalOut = System.out
  val heldBack = ByteArrayOutputStream()
  di = DI {
    extend(originalDi)
    bindSingleton<DetailsOnCI>(overrides = true) { ArtifactsOfOneTest(artifactsUnder) }
  }
  System.setOut(PrintStream(heldBack, true, Charsets.UTF_8.name()))
  try {
    body()
  }
  finally {
    System.out.flush()
    System.setOut(originalOut)
    di = originalDi
  }

  val reported = mutableListOf<ReportedMetadata>()
  for (line in heldBack.toString(Charsets.UTF_8.name()).lineSequence()) {
    val metadata = line.reportedMetadata()
    if (metadata == null) originalOut.println(line) else reported.add(metadata)
  }
  return reported
}

/** This line read as a `testMetadata` message about the artifacts of a launch, or `null` when it is anything else. */
private fun String.reportedMetadata(): ReportedMetadata? {
  if (!startsWith(TEST_METADATA_MESSAGE_PREFIX)) return null
  val value = METADATA_VALUE.find(this)?.groupValues?.get(1)?.takeIf { it.startsWith(ARTIFACTS_URL_PREFIX) } ?: return null
  val name = METADATA_NAME.find(this)?.groupValues?.get(1) ?: return null
  return ReportedMetadata(name, value)
}

/** Links the artifacts of launches publishing under [artifactsUnder], and only those. */
private class ArtifactsOfOneTest(private val artifactsUnder: String) : DetailsOnCI {
  override fun getLinkToCIArtifacts(ideReportingData: IDEReportingData): String? {
    val artifactPath = ideReportingData.artifactPath
    if (artifactPath != artifactsUnder && !artifactPath.startsWith("$artifactsUnder/")) return null
    return linkToArtifacts(artifactPath)
  }
}
