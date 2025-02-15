package com.intellij.lang.xml

fun BasicXmlElementFactory(): BasicXmlElementFactory =
  Class.forName("com.intellij.lang.xml.BackendXmlElementFactory")
    .getConstructor()
    .newInstance() as BasicXmlElementFactory
