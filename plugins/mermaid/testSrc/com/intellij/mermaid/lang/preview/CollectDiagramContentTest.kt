package com.intellij.mermaid.lang.preview

import com.intellij.mermaid.lang.MermaidTestingUtil.obtainTestDataPath
import com.intellij.mermaid.markdown.preview.MermaidDiagramPreviewComponent
import com.intellij.mermaid.markdown.preview.executeCancellableJavaScript
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.io.path.readText

@DisabledInAutomation
@PreviewTest
@WithJcef
@ExtendWith(WaitForBuiltInServerExtension::class)
@TestApplication
class CollectDiagramContentTest {
  @JvmField
  @RegisterExtension
  val projectModel = ProjectModelExtension()

  @TestDisposable
  lateinit var disposable: Disposable

  private val project: Project
    get() = projectModel.project

  private val testDataPath: Path
    get() = obtainTestDataPath().resolve("preview/render")

  @Test
  fun `preview can render minimal flowchart`() {
    // language=Mermaid
    val diagramContent = """
    ---
    title: Node
    ---
    flowchart LR
      id
    """.trimIndent()
    doTest(diagramContent, "minimal-flowchart.svg")
  }

  @Test
  fun `preview can render sequence diagram`() {
    // language=Mermaid
    val diagramContent = """
    sequenceDiagram
      Alice ->> John: Hello John, how are you?
      John -->> Alice: Great!
      Alice -) John: See you later!
    """.trimIndent()
    doTest(diagramContent, "sequence.svg")
  }

  @Test
  fun `preview can render class diagram`() {
    // language=Mermaid
    val diagramContent = """
    ---
    title: Animal example
    ---
    classDiagram
      note "From Duck till Zebra"
      Animal <|-- Duck
      note for Duck "can fly\ncan swim\ncan dive\ncan help in debugging"
      Animal <|-- Fish
      Animal <|-- Zebra
      Animal : +int age
      Animal : +String gender
      Animal: +isMammal()
      Animal: +mate()
      class Duck{
        +String beakColor
        +swim()
        +quack()
      }
      class Fish{
        -int sizeInFeet
        -canEat()
      }
      class Zebra{
        +bool is_wild
        +run()
      }
    """.trimIndent()
    doTest(diagramContent, "class.svg")
  }

  @Test
  fun `preview can render state diagram`() {
    // language=Mermaid
    val diagramContent = """
    ---
    title: Simple sample
    ---
    stateDiagram-v2
      [*] --> Still
      Still --> [*]
      
      Still --> Moving
      Moving --> Still
      Moving --> Crash
      Crash --> [*]
    """.trimIndent()
    doTest(diagramContent, "state.svg")
  }

  @Test
  fun `preview can render entity relation diagram`() {
    // language=Mermaid
    val diagramContent = """
    ---
    title: Order example
    ---
    erDiagram
      CUSTOMER ||--o{ ORDER : places
      ORDER ||--|{ LINE-ITEM : contains
      CUSTOMER }|..|{ DELIVERY-ADDRESS : uses
    """.trimIndent()
    doTest(diagramContent, "er.svg")
  }

  @Test
  fun `preview can render user journey diagram`() {
    // language=Mermaid
    val diagramContent = """
    journey
      title My working day
      section Go to work
        Make tea: 5: Me
        Go upstairs: 3: Me
        Do work: 1: Me, Cat
      section Go home
        Go downstairs: 5: Me
        Sit down: 5: Me
    """.trimIndent()
    doTest(diagramContent, "journey.svg")
  }

  @Disabled("Something is broken with styles")
  @Test
  fun `preview can render gantt diagram`() {
    // language=Mermaid
    val diagramContent = """
    gantt
      title A Gantt Diagram
      dateFormat  YYYY-MM-DD
      section Section
        A task           :a1, 2014-01-01, 30d
        Another task     :after a1  , 20d
      section Another
        Task in sec      :2014-01-12  , 12d
        another task      : 24d
    """.trimIndent()
    doTest(diagramContent, "gantt.svg")
  }

  @Disabled("Pie diagram rendering is broken in Mermaid itself, so the resulting SVG is also broken")
  @Test
  fun `preview can render pie diagram`() {
    // language=Mermaid
    val diagramContent = """
    pie title Pets adopted by volunteers
      "Dogs" : 386
      "Cats" : 85
      "Rats" : 15
    """.trimIndent()
    doTest(diagramContent, "pie.svg")
  }

  @Test
  fun `preview can render quadrant diagram`() {
    // language=Mermaid
    val diagramContent = """
    quadrantChart
      title Reach and engagement of campaigns
      x-axis Low Reach --> High Reach
      y-axis Low Engagement --> High Engagement
      quadrant-1 We should expand
      quadrant-2 Need to promote
      quadrant-3 Re-evaluate
      quadrant-4 May be improved
      Campaign A: [0.3, 0.6]
      Campaign B: [0.45, 0.23]
      Campaign C: [0.57, 0.69]
      Campaign D: [0.78, 0.34]
      Campaign E: [0.40, 0.34]
      Campaign F: [0.35, 0.78]
    """.trimIndent()
    doTest(diagramContent, "quadrant.svg")
  }

  @Test
  fun `preview can render requirement diagram`() {
    // language=Mermaid
    val diagramContent = """
    requirementDiagram
      requirement test_req {
        id: 1
        text: the test text.
        risk: high
        verifymethod: test
      }
  
      element test_entity {
        type: simulation
      }
  
      test_entity - satisfies -> test_req
    """.trimIndent()
    doTest(diagramContent, "requirement.svg")
  }

  @Disabled("Checking against predefined SVG won't work since the commit hashes are randomized")
  @Test
  fun `preview can render git graph diagram`() {
    // language=Mermaid
    val diagramContent = """
    ---
    title: Example Git diagram
    ---
    gitGraph
      commit
      commit
      branch develop
      checkout develop
      commit
      commit
      checkout main
      merge develop
      commit
      commit
    """.trimIndent()
    doTest(diagramContent, "git-graph.svg")
  }

  @Disabled("Broken dimensions")
  @Test
  fun `preview can render mindmap diagram`() {
    // language=Mermaid
    val diagramContent = """
    mindmap
      root((mindmap))
        Origins
          Long history
          ::icon(fa fa-book)
          Popularisation
            British popular psychology author Tony Buzan
        Research
          On effectiveness<br/>and features
          On Automatic creation
            Uses
                Creative techniques
                Strategic planning
                Argument mapping
        Tools
          Pen and paper
          Mermaid
    """.trimIndent()
    doTest(diagramContent, "mindmap.svg")
  }

  @Test
  fun `preview can render timeline diagram`() {
    // language=Mermaid
    val diagramContent = """
    timeline
      title History of Social Media Platform
      2002 : LinkedIn
      2004 : Facebook
           : Google
      2005 : Youtube
      2006 : Twitter
    """.trimIndent()
    doTest(diagramContent, "timeline.svg")
  }

  private fun doTest(diagramContent: String, fileName: String) {
    val rendered = renderDiagram(diagramContent)
    val expected = loadFileContent(fileName)
    Assertions.assertEquals(expected, rendered)
  }

  private fun loadFileContent(name: String): String {
    val path = testDataPath.resolve(name)
    return path.readText().trimEnd('\n')
  }

  private fun renderDiagram(@Language("Mermaid") diagramContent: String): String {
    return runBlocking(Dispatchers.EDT) {
      val preview = MermaidDiagramPreviewComponent(project)
      Disposer.register(disposable, preview)
      preview.load()
      preview.update(diagramContent)
      return@runBlocking preview.collectRenderedDiagram()
    }
  }

  private suspend fun MermaidDiagramPreviewComponent.collectRenderedDiagram(): String {
    // language=JavaScript
    val code = """new Promise(resolve => resolve(window["collectDiagramContent"]()));"""
    val result = browser.executeCancellableJavaScript(code)
    checkNotNull(result) { "collectDiagramContent returned no result" }
    return result
  }
}
