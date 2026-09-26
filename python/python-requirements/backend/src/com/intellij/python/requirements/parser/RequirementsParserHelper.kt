package com.intellij.python.requirements.parser

import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
object RequirementsParserHelper {
  /**
   * List of supported VCS URL schemes.
   */
  val VCS_SCHEMES: List<String> = listOf(
    "git",
    "git+https",
    "git+ssh",
    "git+git",
    "hg+http",
    "hg+https",
    "hg+static-http",
    "hg+ssh",
    "svn",
    "svn+svn",
    "svn+http",
    "svn+https",
    "svn+ssh",
    "bzr+http",
    "bzr+https",
    "bzr+ssh",
    "bzr+sftp",
    "bzr+ftp",
    "bzr+lp"
  )
}