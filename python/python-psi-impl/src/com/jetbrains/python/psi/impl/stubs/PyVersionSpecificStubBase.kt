package com.jetbrains.python.psi.impl.stubs

import com.google.common.collect.*
import com.intellij.openapi.util.Version
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.*
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.stubs.PyVersionSpecificStub
import java.math.BigInteger

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
  return CachedValuesManager.getCachedValue(element) {
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
      val refExpr = PyPsiUtils.flattenParens(binaryExpr.leftExpression) as? PyReferenceExpression ?: return null
      if (SYS_VERSION_INFO_QUALIFIED_NAME != refExpr.asQualifiedName()) return null

      val tuple = PyPsiUtils.flattenParens(binaryExpr.rightExpression) as? PyTupleExpression ?: return null
      val version = evaluateVersion(tuple) ?: return null

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

private val SYS_VERSION_INFO_QUALIFIED_NAME = QualifiedName.fromDottedString("sys.version_info")

private fun evaluateVersion(versionTuple: PyTupleExpression): Version? {
  val elements = versionTuple.elements
  if (elements.size != 1 && elements.size != 2) {
    return null
  }

  val major = evaluateNumber(elements[0])
  if (major == null) {
    return null
  }

  if (elements.size == 1) {
    return Version(major, 0, 0)
  }

  val minor = evaluateNumber(elements[1])
  if (minor == null) {
    return null
  }

  return Version(major, minor, 0)
}

private fun evaluateNumber(expression: PyExpression?): Int? {
  if (expression !is PyNumericLiteralExpression) return null
  if (!expression.isIntegerLiteral) return null
  val value = expression.bigIntegerValue
  val intValue = value.toInt()
  return if (BigInteger.valueOf(intValue.toLong()) == value) intValue else null
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