package com.jetbrains.python.psi.impl.stubs

import com.intellij.openapi.util.Version
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.*
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElsePart
import com.jetbrains.python.psi.PyIfPart
import com.jetbrains.python.psi.PyIfStatement
import com.jetbrains.python.psi.impl.PyVersionCheck
import com.jetbrains.python.psi.impl.isLessThan
import com.jetbrains.python.psi.stubs.PyVersionRange
import com.jetbrains.python.psi.stubs.PyVersionSpecificStub

internal abstract class PyVersionSpecificStubBase<T : PsiElement>(
  parent: StubElement<*>?,
  elementType: IStubElementType<*, *>?,
  override val versionRange: PyVersionRange,
) : StubBase<T>(parent, elementType), PyVersionSpecificStub

internal fun getChildrenStubs(stub: StubElement<*>, languageLevel: LanguageLevel): Iterable<StubElement<*>> {
  return stub.childrenStubs.asSequence()
    .filter { it !is PyVersionSpecificStub || it.versionRange.contains(languageLevel) }
    .asIterable()
}

private fun PyVersionRange.contains(languageLevel: LanguageLevel): Boolean {
  val low = lowInclusive
  val high = highExclusive
  return (low == null || !languageLevel.isLessThan(low)) &&
         (high == null || languageLevel.isLessThan(high))
}

internal fun evaluateVersionRangeForElement(element: PsiElement): PyVersionRange {
  return CachedValuesManager.getCachedValue(element) {
    val parent = element.parent
    var range: PyVersionRange
    if (parent == null) {
      range = PyVersionRange(null, null)
    }
    else {
      range = evaluateVersionRangeForElement(parent)
      if (parent is PyIfPart || parent is PyElsePart) {
        val grandParent = parent.parent
        if (grandParent is PyIfStatement) {
          range = evaluateRange(range, grandParent, parent)
        }
      }
    }
    CachedValueProvider.Result.create(range, element)
  }
}

private fun evaluateRange(
  initialRange: PyVersionRange,
  ifStatement: PyIfStatement,
  ifStatementPart: PsiElement,
): PyVersionRange {
  val versionChecks = mutableListOf<PyVersionCheck>()
  val ifParts = sequenceOf(ifStatement.ifPart) + ifStatement.elifParts.asSequence()
  for (ifPart in ifParts.takeWhile { it !== ifStatementPart }) {
    val versionCheck = PyVersionCheck.fromCondition(ifPart) ?: return initialRange
    versionChecks.add(PyVersionCheck(versionCheck.version, !versionCheck.isLessThan))
  }
  if (ifStatementPart is PyIfPart) {
    val versionCheck = PyVersionCheck.fromCondition(ifStatementPart) ?: return initialRange
    versionChecks.add(versionCheck)
  }
  return versionChecks.fold(initialRange, ::clampRange)
}

private fun clampRange(versionRange: PyVersionRange, versionCheck: PyVersionCheck): PyVersionRange {
  return if (versionCheck.isLessThan)
    PyVersionRange(versionRange.lowInclusive, min(versionRange.highExclusive, versionCheck.version))
  else
    PyVersionRange(max(versionRange.lowInclusive, versionCheck.version), versionRange.highExclusive)
}

private fun min(a: Version?, b: Version): Version {
  return if (a == null) b else minOf(a, b)
}

private fun max(a: Version?, b: Version): Version {
  return if (a == null) b else maxOf(a, b)
}

internal fun serializeVersionRange(versionRange: PyVersionRange, outputStream: StubOutputStream) {
  serializeVersion(versionRange.lowInclusive, outputStream)
  serializeVersion(versionRange.highExclusive, outputStream)
}

private fun serializeVersion(version: Version?, outputStream: StubOutputStream) {
  outputStream.writeBoolean(version != null)
  if (version != null) {
    outputStream.writeVarInt(version.major)
    outputStream.writeVarInt(version.minor)
  }
}

internal fun deserializeVersionRange(stream: StubInputStream): PyVersionRange {
  val lowInclusive = deserializeVersion(stream)
  val highExclusive = deserializeVersion(stream)
  return PyVersionRange(lowInclusive, highExclusive)
}

private fun deserializeVersion(stream: StubInputStream): Version? {
  val isNotNull = stream.readBoolean()
  if (isNotNull) {
    val major = stream.readVarInt()
    val minor = stream.readVarInt()
    return Version(major, minor, 0)
  }
  return null
}