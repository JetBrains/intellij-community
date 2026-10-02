// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus;

/**
 * User : catherine
 */
@ApiStatus.Internal
public final class PyEncodingUtil {

  private PyEncodingUtil() {
  }

  public static final @NlsSafe String[] POSSIBLE_ENCODINGS =
    new String[]{"ascii", "big5", "big5hkscs", "cp037", "cp424", "cp437", "cp500", "cp720", "cp737",
      "cp775", "cp850", "cp852", "cp855", "cp856", "cp857", "cp858", "cp860", "cp861", "cp862", "cp863", "cp864", "cp865", "cp866", "cp869",
      "cp874",
      "cp875", "cp932", "cp949", "cp950", "cp1006", "cp1026", "cp1140", "cp1250", "cp1251", "cp1252", "cp1253", "cp1254", "cp1255",
      "cp1256", "cp1257",
      "cp1258", "euc-jp", "euc-jis-2004", "euc-kr", "gb2312", "gbk", "gb18030", "hz", "iso2022-jp", "iso2022-jp-1", "iso2022-jp-2",
      "iso2022-jp-2004",
      "iso2022-jp-3", "iso2022-jp-ext", "iso2022-kr", "latin-1", "iso8859-2", "iso8859-3", "iso8859-4", "iso8859-5", "iso8859-6",
      "iso8859-7",
      "iso8859-8", "iso8859-9", "iso8859-10", "iso8859-13", "iso8859-14", "iso8859-15", "iso8859-16", "johab", "koi8-r", "koi8-u",
      "mac-cyrillic",
      "mac-greek", "mac-iceland", "mac-latin2", "mac-roman", "mac-turkish", "ptcp154", "shift-jis", "shift-jis-2004", "shift-jisx0213",
      "utf-32",
      "utf-32-be", "utf-32-le", "utf-16", "utf-16-be", "utf-16-le", "utf-7", "utf-8", "utf-8-sig"};

  public static final @NlsSafe String[] ENCODING_FORMAT =
    new String[]{"# coding=<encoding name>", "# -*- coding: <encoding name> -*-", "# vim: set fileencoding=<encoding name> :"};
  public static final @NlsSafe String[] ENCODING_FORMAT_PATTERN =
    new String[]{"# coding=%s", "# -*- coding: %s -*-", "# vim: set fileencoding=%s :"};
}
