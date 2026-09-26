package com.intellij.searchEverywhereLucene.backend.providers.files.analysis

enum class FileTokenType(val type: String) {
  PATH("path"),
  PATH_SEGMENT("pathSegment"),
  PATH_SEGMENT_PREFIX("pathSegmentPrefix"),
  FILENAME("filename"),
  FILENAME_PART("filenamePart"),
  FILENAME_ABBREVIATION("filenameAbbreviation"),
  FILENAME_ABBREVIATION_WITH_SKIPS("filenameAbbreviationWithSkips"),
  FILETYPE("filetype");
}
