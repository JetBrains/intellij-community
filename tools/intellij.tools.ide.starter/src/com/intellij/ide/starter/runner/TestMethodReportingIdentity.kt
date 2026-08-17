package com.intellij.ide.starter.runner

import com.intellij.ide.starter.utils.ReportingPathUtils
import com.intellij.ide.starter.utils.ReportingPathUtils.dirName
import com.intellij.ide.starter.utils.escapeDotSegment
import com.intellij.ide.starter.utils.flattened
import com.intellij.ide.starter.utils.startsWithWholeName
import org.jetbrains.annotations.ApiStatus

/** The test method an IDE launch belongs to, and which run of that method inside one IDE process it is. */
@ApiStatus.Internal
data class TestMethodReportingIdentity(
  val className: String,
  val displayName: String,
  /** 1-based order of this method's first run inside one IDE process. */
  val executionIndex: Int,
) {
  /** What the method is called, one level at a time, as it was given: the class it is in and the method, each when it is known. */
  val rawSegments: List<String> = listOf(className, displayName).filter(String::isNotEmpty)

  private val dirClassName: String? = className.takeUnless(String::isEmpty)?.flattened()
  private val dirMethodName: String? = displayName.takeUnless(String::isEmpty)?.flattened()
  private val dirSegments: List<String> = listOfNotNull(dirClassName, dirMethodName)

  /**
   * One bounded directory name per level of the method, the class first, the last prefixed with [executionIndex] so that the order the
   * methods ran in shows up in the reporting tree. The class is left out when [theClassIsNamedAbove] — never when it is the only name there
   * is, the index being on the last.
   */
  fun dirNames(theClassIsNamedAbove: Boolean): List<String> {
    val names = dirSegments.mapIndexed { segmentIndex, segment ->
      val indexPrefix = if (segmentIndex == dirSegments.lastIndex) "${executionIndex}_" else ""
      dirName(segment.escapeDotSegment(), prefix = indexPrefix)
    }
    return if (theClassIsNamedAbove && names.size > 1) names.drop(1) else names
  }

  /**
   * Whether [flattenedTestName] is this method's name: the whole of it, class and all, or the method's own name and nothing besides. An
   * equality, because a project name that merely runs into `class-method` names a project of its own.
   *
   * Only ever true of a method whose class is known, because the answer is what makes a published path leave the test out — and the
   * class is then all that is left to name the test in its place.
   */
  fun namesTheTest(flattenedTestName: String): Boolean = dirClassName != null &&
                                                         (flattenedTestName == dirSegments.joinToString("-") ||
                                                          flattenedTestName == dirMethodName)

  /**
   * Whether [flattenedTestName] begins with the class this method is in, and so names it before any directory below could. The front of it,
   * because that is the part [ReportingPathUtils.testDirectoryName] keeps when it cuts the test's own directory down to a bounded length.
   */
  fun hasItsClassNamedBy(flattenedTestName: String): Boolean =
    dirClassName != null && flattenedTestName.startsWithWholeName(dirClassName)

  /** Whether [flattenedLaunchName] is this method's own name and nothing besides. */
  fun namesTheLaunch(flattenedLaunchName: String?): Boolean = flattenedLaunchName != null && flattenedLaunchName == dirMethodName
}
