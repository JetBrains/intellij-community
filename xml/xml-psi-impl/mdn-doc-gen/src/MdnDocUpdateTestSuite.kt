import com.intellij.codeInsight.XmlDocumentationTest
import com.intellij.html.HtmlDocumentationTest
import com.intellij.htmltools.codeInsight.daemon.HtmlHighlightingTest
import com.intellij.javascript.webSymbols.css.WebSymbolsCssCodeCompletionTest
import com.intellij.javascript.webSymbols.html.WebSymbolsHtmlDocumentationTest
import com.intellij.javascript.webSymbols.html.WebSymbolsHtmlLookupDocumentationTest
import com.intellij.lang.javascript.JSDocumentationTest
import com.intellij.lang.javascript.typescript.TypeScriptDocumentationTest
import com.intellij.lang.javascript.typescript.service.TypeScriptServiceDocumentationTest
import com.intellij.react.ReactDocumentationTest
import com.intellij.react.tsc.ReactDocumentationWithServiceTest
import com.jetbrains.rider.test.cases.markup.documentation.BlazorDocumentationTest
import css.CssDocumentationTest
import org.angular2.codeInsight.Angular2DocumentationTest
import org.angular2.codeInsight.deprecated.Angular2AttributesTest
import org.angular2.resharper.Angular2HtmlCodeCompletionTest
import org.jetbrains.astro.codeInsight.AstroDocumentationTest
import org.jetbrains.vuejs.lang.VueCompletionTest
import org.jetbrains.vuejs.lang.VueDocumentationTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
  HtmlDocumentationTest::class,
  XmlDocumentationTest::class,
  HtmlHighlightingTest::class,
  WebSymbolsHtmlDocumentationTest::class,
  WebSymbolsHtmlLookupDocumentationTest::class,
  WebSymbolsCssCodeCompletionTest::class,
  CssDocumentationTest::class,
  JSDocumentationTest::class,
  TypeScriptDocumentationTest::class,
  TypeScriptServiceDocumentationTest::class,
  ReactDocumentationTest::class,
  ReactDocumentationWithServiceTest::class,
  Angular2DocumentationTest::class,
  Angular2AttributesTest::class,
  Angular2HtmlCodeCompletionTest::class,
  VueDocumentationTest::class,
  VueCompletionTest::class,
  AstroDocumentationTest::class,
  // Run "Compile Rider Backend" before running this test
  BlazorDocumentationTest::class,
)
class MdnDocUpdateTestSuite