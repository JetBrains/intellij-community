import com.intellij.codeInsight.XmlDocumentationTest
import com.intellij.html.HtmlDocumentationTest
import com.intellij.htmltools.codeInsight.daemon.HtmlHighlightingTest
import com.intellij.javascript.polySymbols.css.PolySymbolsCssCodeCompletionTest
import com.intellij.javascript.polySymbols.html.PolySymbolsHtmlDocumentationTest
import com.intellij.javascript.polySymbols.html.PolySymbolsHtmlLookupDocumentationTest
import com.intellij.lang.javascript.JSDocumentationTest
import com.intellij.lang.javascript.typescript.TypeScriptDocumentationTest
import com.intellij.lang.javascript.typescript.service.TypeScriptServiceDocumentationTest
import com.intellij.react.ReactDocumentationTest
import com.intellij.react.tsc.ReactDocumentationWithServiceTest
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
  PolySymbolsHtmlDocumentationTest::class,
  PolySymbolsHtmlLookupDocumentationTest::class,
  PolySymbolsCssCodeCompletionTest::class,
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
  /* Additionally, run `Compile Rider Backend` and the `BlazorDocumentationTest` */
)
class MdnDocUpdateTestSuite