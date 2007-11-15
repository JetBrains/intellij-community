/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.fileTypes;

import com.intellij.lang.Language;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 15, 2006
 * Time: 7:42:03 PM
 * To change this template use File | Settings | File Templates.
 */
public class PlainTextLanguage extends Language {
  protected PlainTextLanguage() {
    super("TEXT", "text/plain");
  }
}
