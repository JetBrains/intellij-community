package com.intellij.python.junit5Tests.unit

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileFactory
import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.metaInfo.Repository
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.requirements.RequirementsFile
import com.intellij.python.requirements.RequirementsLanguage
import com.intellij.python.requirements.requirements
import com.intellij.python.requirements.parser.psi.NameReq
import com.intellij.python.requirements.parser.psi.Option
import com.intellij.python.requirements.parser.psi.PathReq
import com.intellij.python.requirements.parser.psi.UriReference
import com.intellij.python.requirements.parser.psi.UrlReq
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@PyDefaultTestApplication
@TestClassInfo(repository = Repository.PY_PROFESSIONAL)
class RequirementsFileTest(val project: Project) {
  fun <T: PsiElement> doTestSingleElement(text: String, type: Class<T>, predicate: (T) -> Unit) {
    runReadAction {
      val file = PsiFileFactory
        .getInstance(project)
        .createFileFromText("", RequirementsLanguage, text) as RequirementsFile
      val elements = file.children.filterIsInstance(type)
      val firstError = file.children.filterIsInstance<PsiErrorElement>().firstOrNull()
      if (firstError != null) fail("Parse error: ${firstError.errorDescription}")
      assertEquals(1, elements.size)
      predicate(elements[0])
    }
  }

  @Test
  fun testShortOptionWithArgument() {
    doTestSingleElement("-i https://example.org/pypi", Option::class.java) {
      assertEquals("-i", it.shortOption?.shortOptionName?.text)
      assertEquals("https://example.org/pypi", it.shortOption?.optionValue?.text)
    }
  }

  @Test
  fun testLongOptionWithArgument() {
    doTestSingleElement("--find-links /my/local/archives", Option::class.java) {
      assertEquals("--find-links", it.longOption?.longOptionName?.text)
      assertEquals("/my/local/archives", it.longOption?.optionValue?.text)
    }
  }

  @Test
  fun testLongOptionWithoutArgument() {
    doTestSingleElement("--pre", Option::class.java) {
      assertEquals("--pre", it.longOption?.longOptionName?.text)
      assertNull(it.longOption?.optionValue)
    }
  }

  @Test
  fun testNameReq() {
    doTestSingleElement("example-distro", NameReq::class.java) {
      assertEquals("example-distro", it.packageName.text)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["example-distro[extra1,extra2]", "example-distro [extra1,extra2]", "example-distro[extra1, extra2]"])
  fun testNameReqWithExtras(value: String) {
    doTestSingleElement(value, NameReq::class.java) {
      assertEquals("example-distro", it.packageName.text)
      assertEquals(2, it.extras?.extrasList?.packageNameList?.size)
      assertEquals("extra1", it.extras?.extrasList?.packageNameList[0]?.text)
      assertEquals("extra2", it.extras?.extrasList?.packageNameList[1]?.text)
    }
  }

  @Test
  fun testNameReqWithVersion() {
    doTestSingleElement("example-distro < 1.0.5,>= 0.9.2", NameReq::class.java) {
      assertEquals("example-distro", it.packageName.text)
      assertEquals(2, it.versionspec?.versionOneList?.size)
      assertEquals("<", it.versionspec!!.versionOneList[0].versionCmp.text)
      assertEquals("1.0.5", it.versionspec!!.versionOneList[0].version?.text)
      assertEquals(">=", it.versionspec!!.versionOneList[1].versionCmp.text)
      assertEquals("0.9.2", it.versionspec!!.versionOneList[1].version?.text)
    }
  }

  @Test
  fun testUrlReq() {
    doTestSingleElement("example-distro [extra1,extra2] @ https://example.org/pypi/example-distro-1.0.0.tar.gz ; python_version < \"3.14\"", UrlReq::class.java) {
      assertEquals("example-distro", it.packageName.text)
      assertEquals("https://example.org/pypi/example-distro-1.0.0.tar.gz", it.uri?.text)
      assertEquals(2, it.extras?.extrasList?.packageNameList?.size)
      assertEquals("extra1", it.extras?.extrasList?.packageNameList[0]?.text)
      assertEquals("extra2", it.extras?.extrasList?.packageNameList[1]?.text)
      assertEquals("python_version < \"3.14\"", it.quotedMarker?.markerOr?.text)
    }
  }

  @Test
  fun testMultiLineReq() {
    doTestSingleElement("example-distro < 12.0,\\\n>=13.0", NameReq::class.java) {
      assertEquals("example-distro", it.packageName.text)
      assertEquals(2, it.versionspec?.versionOneList?.size)
      assertEquals("<", it.versionspec!!.versionOneList[0].versionCmp.text)
      assertEquals("12.0", it.versionspec!!.versionOneList[0].version?.text)
      assertEquals(">=", it.versionspec!!.versionOneList[1].versionCmp.text)
      assertEquals("13.0", it.versionspec!!.versionOneList[1].version?.text)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["example.org", "[::1]", "[::1]:80", "127.0.0.1:80"])
  fun testUriReference(hostPort: String) {
    val uri = "https://${hostPort}/pypi/idna-3.11.tar.gz"
    doTestSingleElement(uri, UriReference::class.java) {
      assertEquals(uri, it.text)
      assertEquals(hostPort, it.uri!!.authority.text)
      assertEquals("https", it.uri!!.scheme.text)
      assertEquals("/pypi/idna-3.11.tar.gz", it.uri!!.uriPath?.text)
    }
  }

  @Test
  fun testUriReferenceWithComment() {
    val uri = "https://example.org/pypi/idna-3.11.tar.gz #comment"
    doTestSingleElement(uri, UriReference::class.java) {
      assertEquals("https://example.org/pypi/idna-3.11.tar.gz", it.text)
      assertEquals("https", it.uri!!.scheme.text)
      assertEquals("example.org", it.uri!!.authority.text)
      assertEquals("/pypi/idna-3.11.tar.gz", it.uri!!.uriPath?.text)
      assertNull(it.uri!!.fragment)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["example.org", "[::1]", "[::1]:80", "127.0.0.1:80"])
  fun testUriReferenceWithEnvMarkers(hostPort: String) {
    val uri = "https://${hostPort}/pypi/idna-3.11.tar.gz; python_version < \"3.14\""
    doTestSingleElement(uri, UriReference::class.java) {
      assertEquals(uri, it.text)
      assertEquals("https://${hostPort}/pypi/idna-3.11.tar.gz", it.uri!!.text)
      assertEquals(hostPort, it.uri!!.authority.text)
      assertEquals("python_version < \"3.14\"", it.quotedMarker?.markerOr?.text)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["./foo/test", "/foo/test", "C:\\foo\\test"])
  fun testSimplePathReq(path: String) {
    doTestSingleElement(path, PathReq::class.java) {
      assertEquals(path, it.path.text)
    }
  }

  @ParameterizedTest
  @CsvSource(value = [
    "-e ./foo/test[extra1, extra2]|./foo/test|extra1,extra2",
    "-e /foo/test [extra1,extra2]|/foo/test|extra1,extra2",
    "-e C:\\foo\\test[extra1]|C:\\foo\\test|extra1"
  ], delimiter = '|')
  fun testComplexPathReq(line: String, path: String, extras: String) {
    doTestSingleElement(line, PathReq::class.java) {
      assertEquals(path, it.path.text)
      assertEquals("-e", it.editableOption?.text)
      val joinedExtras = it.extras?.extrasList?.packageNameList?.joinToString(",") { extra -> extra.text }
      assertNotNull(joinedExtras)
      assertEquals(extras, joinedExtras)
    }
  }

  @Test
  fun testErrorRecovery() {
    val text = """
      example >=
      working-package
      https://
      another-working-package == 1.0.0
    """.trimIndent()
    runReadAction {
      val file = PsiFileFactory.getInstance(project).createFileFromText("", RequirementsLanguage, text) as RequirementsFile
      val requirements = file.requirements()
      assertEquals(2, requirements.size)
      assertEquals("working-package", requirements[0].name)
      assertEquals("another-working-package", requirements[1].name)
      assertEquals(1, requirements[1].versionSpecs.size)
    }
  }

  @Test
  fun testParseRequirements() {
    val text = """
      #beginning comment
      -i https://pypi.org/simple/
      --pre
      urllib3 @ \
       git+https://github.com/jschneider/urllib3.git
      mypackage [extra1,extra2] @ file:///foo/bar
      Pillow >= 12.0.0,\
       < 13.0a5
      # commented line, empty line next

       # comment with whitespace at the beginning
      https://example.org/pypi/idna-3.11.tar.gz  # comment
      https://example.org/pypi/foobar-1.2.3.tar.gz#egg=foobar1  # comment
      -e Django == 5.2.7
      --editable .
    """.trimIndent()
    runReadAction {
      val file = PsiFileFactory.getInstance(project).createFileFromText("", RequirementsLanguage, text) as RequirementsFile
      val firstError = file.children.filterIsInstance<PsiErrorElement>().firstOrNull()
      if (firstError != null) fail("Parse error: ${firstError.errorDescription}")

      val requirements = file.requirements()
      assertEquals(4, requirements.size)

      assertEquals("urllib3", requirements[0].name)
      assertEquals("git+https://github.com/jschneider/urllib3.git", requirements[0].urlReference)

      assertEquals("mypackage", requirements[1].name)
      assertEquals("file:///foo/bar", requirements[1].urlReference)
      assertEquals("extra1,extra2", requirements[1].extras)

      assertEquals("pillow", requirements[2].name)
      assertEquals(2, requirements[2].versionSpecs.size)
      assertEquals("12.0.0", requirements[2].versionSpecs[0].version)
      assertEquals(PyRequirementRelation.GTE, requirements[2].versionSpecs[0].relation)
      assertEquals("13.0a5", requirements[2].versionSpecs[1].version)
      assertEquals(PyRequirementRelation.LT, requirements[2].versionSpecs[1].relation)

      assertEquals("django", requirements[3].name)
      assertEquals(1, requirements[3].versionSpecs.size)
      assertEquals("5.2.7", requirements[3].versionSpecs[0].version)
      assertEquals(PyRequirementRelation.EQ, requirements[3].versionSpecs[0].relation)
    }
  }
}