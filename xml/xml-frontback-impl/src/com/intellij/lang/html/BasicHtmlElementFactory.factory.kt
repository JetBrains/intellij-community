package com.intellij.lang.html

fun BasicHtmlElementFactory(): BasicHtmlElementFactory =
  Class.forName("com.intellij.lang.html.BackendHtmlElementFactory")
    .getConstructor()
    .newInstance() as BasicHtmlElementFactory

