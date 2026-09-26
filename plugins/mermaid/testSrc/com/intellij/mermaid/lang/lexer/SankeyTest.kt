package com.intellij.mermaid.lang.lexer

class SankeyTest : MermaidLexerTestCase() {
  override val diagramName: String
    get() = "sankey"

  fun `test sankey`() {
    val content = """
      sankey-beta
        Agricultural 'waste',Bio-conversion,124.729
        Bio-conversion,Liquid,0.597
        District heating,Heating and cooling - commercial,22.505
        Electricity grid,Over generation / exports,104.453
        Electricity grid,Heating and cooling - homes,113.726
        Electricity grid,Lighting & appliances - commercial,90.008
        Pumped heat,"Heating and cooling, homes",193.026
        Pumped heat,"Heating and cooling, commercial",70.672
        Pumped heat,"Heating and cooling, ""homes""${'"'},193.026
        Pumped heat,"Heating and cooling, ""commercial""${'"'},70.672
    """.trimIndent()
    doTest(content)
  }
}
