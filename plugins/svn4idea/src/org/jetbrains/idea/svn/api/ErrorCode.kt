// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.api

import org.jetbrains.idea.svn.api.ErrorCategory.*

private val ERROR_BASE = 120000
private val ERROR_CATEGORY_SIZE = 5000

enum class ErrorCategory(val code: Int) {
  ENTRY(6),
  WC(7),
  FS(8),
  RA(10),
  RA_DAV(11),
  CLIENT(15),
  MISC(16),
  AUTHN(19),
  AUTHZ(20);

  val start = ERROR_BASE + code * ERROR_CATEGORY_SIZE

  companion object {
    @JvmStatic
    fun categoryCodeOf(errorCode: Int) = (errorCode - ERROR_BASE) / ERROR_CATEGORY_SIZE
  }
}

enum class ErrorCode(val category: ErrorCategory, val index: Int) {
  ENTRY_EXISTS(ENTRY, 2),

  WC_LOCKED(WC, 4),
  WC_NOT_WORKING_COPY(WC, 7),
  WC_NOT_FILE(WC, 8),
  WC_PATH_NOT_FOUND(WC, 10),
  WC_CORRUPT(WC, 16),
  WC_CORRUPT_TEXT_BASE(WC, 17),
  WC_UNSUPPORTED_FORMAT(WC, 21),
  WC_UPGRADE_REQUIRED(WC, 36),

  FS_NOT_FOUND(FS, 13),

  RA_ILLEGAL_URL(RA, 0),
  RA_NOT_AUTHORIZED(RA, 1),
  RA_UNKNOWN_AUTH(RA, 2),

  RA_DAV_PATH_NOT_FOUND(RA_DAV, 7),

  CLIENT_UNRELATED_RESOURCES(CLIENT, 12),

  BASE(MISC, 0),
  UNVERSIONED_RESOURCE(MISC, 5),
  UNSUPPORTED_FEATURE(MISC, 7),
  ILLEGAL_TARGET(MISC, 9),
  PROPERTY_NOT_FOUND(MISC, 17),
  MERGE_INFO_PARSE_ERROR(MISC, 20);

  val code = category.start + index
}