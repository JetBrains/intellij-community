package com.jetbrains.python.psi.impl.stubs

import com.google.common.collect.BoundType
import com.google.common.collect.ImmutableRangeSet
import com.google.common.collect.Range
import com.google.common.collect.RangeSet
import com.google.common.collect.TreeRangeSet
import com.intellij.openapi.util.Version
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubBuildCachedValuesManager.StubBuildCachedValueProvider
import com.intellij.psi.stubs.StubBuildCachedValuesManager.getCachedValueStubBuildOptimized
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.util.CachedValueProvider
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyElsePart
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyIfPart
import com.jetbrains.python.psi.PyIfStatement
import com.jetbrains.python.psi.PyStatementPart
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.stubs.PyVersionSpecificStub

internal abstract class PyVersionSpecificStubBase<T : PsiElement>(
  parent: StubElement<*>?,
  elementType: IStubElementType<*, *>?,
  override val versions: RangeSet<Version>,
) : StubBase<T>(parent, elementType), PyVersionSpecificStub

internal fun getChildrenStubs(stub: StubElement<*>, languageLevel: LanguageLevel): Iterable<StubElement<*>> {
  val version = Version(languageLevel.majorVersion, languageLevel.minorVersion, 0)
  return stub.childrenStubs.asSequence()
    .filter { it !is PyVersionSpecificStub || it.versions.contains(version) }
    .asIterable()
}

internal fun evaluateVersionsForElement(element: PsiElement): ImmutableRangeSet<Version> {
  return getCachedValueStubBuildOptimized(element, EVAL_VERSIONS_PROVIDER)
}

private val EVAL_VERSIONS_PROVIDER = StubBuildCachedValueProvider<ImmutableRangeSet<Version>, PsiElement>(
  "python.versionsForElement"
) { element ->
  val parent = element.parent
  var result: ImmutableRangeSet<Version>
  if (parent == null) {
    result = ImmutableRangeSet.of(Range.all())
  }
  else {
    result = evaluateVersionsForElement(parent)
    if (parent is PyIfPart || parent is PyElsePart) {
      val grandParent = parent.parent
      if (grandParent is PyIfStatement && element === (parent as PyStatementPart).statementList) {
        val versions = evaluateVersionRangeForIfStatementPart(grandParent, parent)
        if (versions != null) {
          result = result.intersection(versions)
        }
      }
    }
  }
  CachedValueProvider.Result.create(result, element)
}

private fun evaluateVersionRangeForIfStatementPart(ifStatement: PyIfStatement, ifStatementPart: PsiElement): RangeSet<Version>? {
  assert(ifStatementPart is PyIfPart || ifStatementPart is PyElsePart)
  val result = if (ifStatementPart is PyIfPart) {
    val versionRanges = convertToVersionRanges(ifStatementPart) ?: return null
    TreeRangeSet.create(versionRanges)
  }
  else {
    TreeRangeSet.create(listOf(Range.all<Version>()))
  }
  val ifParts = sequenceOf(ifStatement.ifPart) + ifStatement.elifParts.asSequence()
  for (ifPart in ifParts.takeWhile { it !== ifStatementPart }) {
    val versionRanges = convertToVersionRanges(ifPart) ?: return null
    result.removeAll(versionRanges)
  }
  return result
}

/**
 * @return Version ranges if {@code expression} is a version check, {@code null} otherwise
 *
 * @see <a href="https://peps.python.org/pep-0484/#version-and-platform-checking">Version and Platform Checks</a>
 */
private fun convertToVersionRanges(ifPart: PyIfPart): ImmutableRangeSet<Version>? {
  return ifPart.condition?.let { convertToVersionRanges(it) }
}

private fun convertToVersionRanges(expression: PyExpression): ImmutableRangeSet<Version>? {
  val binaryExpr = PyPsiUtils.flattenParens(expression) as? PyBinaryExpression ?: return null
  when (val operator = binaryExpr.operator) {
    PyTokenTypes.AND_KEYWORD, PyTokenTypes.OR_KEYWORD -> {
      val rhs = binaryExpr.rightExpression ?: return null
      val ranges1 = convertToVersionRanges(binaryExpr.leftExpression) ?: return null
      val ranges2 = convertToVersionRanges(rhs) ?: return null
      return if (operator === PyTokenTypes.AND_KEYWORD)
        ranges1.intersection(ranges2)
      else
        ranges1.union(ranges2)
    }

    PyTokenTypes.LT, PyTokenTypes.GT, PyTokenTypes.LE, PyTokenTypes.GE -> {
      if (!PyEvaluator.isSysVersionInfoExpression(binaryExpr.leftExpression)) return null

      val versionArray = PyEvaluator.evaluateAsVersion(binaryExpr.rightExpression) ?: return null
      val major = versionArray.firstOrNull() ?: return null
      val minor = versionArray.getOrElse(1) { 0 }
      val version = Version(major, minor, 0)

      val range = when (operator) {
        PyTokenTypes.LT -> Range.lessThan(version)
        PyTokenTypes.GT -> Range.greaterThan(version)
        PyTokenTypes.LE -> Range.atMost(version)
        PyTokenTypes.GE -> Range.atLeast(version)
        else -> throw IllegalStateException()
      }
      return ImmutableRangeSet.of(range)
    }

    else -> return null
  }
}

internal fun serializeVersions(versions: RangeSet<Version>, outputStream: StubOutputStream) {
  val ranges = versions.asRanges()
  outputStream.writeVarInt(ranges.size)
  for (range in ranges) {
    serializeRange(range, outputStream)
  }
}

private fun serializeRange(range: Range<Version>, outputStream: StubOutputStream) {
  val low = if (range.hasLowerBound()) Endpoint(range.lowerEndpoint(), range.lowerBoundType()) else null
  val high = if (range.hasUpperBound()) Endpoint(range.upperEndpoint(), range.upperBoundType()) else null
  serializeEndpoint(low, outputStream)
  serializeEndpoint(high, outputStream)
}

private fun serializeEndpoint(endpoint: Endpoint?, outputStream: StubOutputStream) {
  if (endpoint == null) {
    outputStream.writeByte(EndpointType.UNBOUND)
  }
  else {
    val endpointType = when (endpoint.boundType) {
      BoundType.OPEN -> EndpointType.OPEN
      BoundType.CLOSED -> EndpointType.CLOSED
    }
    outputStream.writeByte(endpointType)
    outputStream.writeVarInt(endpoint.version.major)
    outputStream.writeVarInt(endpoint.version.minor)
  }
}

internal fun deserializeVersions(stream: StubInputStream): RangeSet<Version> {
  val size = stream.readVarInt()
  val builder = ImmutableRangeSet.builder<Version>()
  repeat(size) {
    builder.add(deserializeRange(stream))
  }
  return builder.build()
}

private fun deserializeRange(stream: StubInputStream): Range<Version> {
  val low = deserializeEndpoint(stream)
  val high = deserializeEndpoint(stream)
  return if (low != null && high != null)
    Range.range(low.version, low.boundType, high.version, high.boundType)
  else if (high != null)
    Range.upTo(high.version, high.boundType)
  else if (low != null)
    Range.downTo(low.version, low.boundType)
  else
    Range.all()
}

private fun deserializeEndpoint(stream: StubInputStream): Endpoint? {
  val endpointType = stream.readByte().toInt()
  val boundType = when (endpointType) {
    EndpointType.UNBOUND -> return null
    EndpointType.OPEN -> BoundType.OPEN
    EndpointType.CLOSED -> BoundType.CLOSED
    else -> throw IllegalArgumentException()
  }
  val major = stream.readVarInt()
  val minor = stream.readVarInt()
  return Endpoint(Version(major, minor, 0), boundType)
}

private data class Endpoint(val version: Version, val boundType: BoundType)

private object EndpointType {
  const val UNBOUND = 0
  const val OPEN = 1
  const val CLOSED = 2
}