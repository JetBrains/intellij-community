package com.jetbrains.python.psi.impl.stubs

import com.intellij.openapi.util.Version
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.impl.PyVersionCheck
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction

internal fun isFileOrClassTopLevel(element: PsiElement): Boolean {
  val parent = PsiTreeUtil.getParentOfType(element, PyFile::class.java, PyClass::class.java, PyFunction::class.java)
  return parent is PyFile || parent is PyClass
}

internal fun serializeVersionCheck(versionCheck: PyVersionCheck, dataStream: StubOutputStream) {
  dataStream.writeBoolean(versionCheck.isLessThan)
  dataStream.writeVarInt(versionCheck.version.major)
  dataStream.writeVarInt(versionCheck.version.minor)
}

internal fun deserializeVersionCheck(dataStream: StubInputStream): PyVersionCheck {
  val isLessThan = dataStream.readBoolean()
  val major = dataStream.readVarInt()
  val minor = dataStream.readVarInt()
  return PyVersionCheck(Version(major, minor, 0), isLessThan)
}
