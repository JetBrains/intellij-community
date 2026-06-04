package com.intellij.mermaid.lang.lexer

class TimelineTest : MermaidLexerTestCase() {
  override val diagramName: String
    get() = "timeline"

  fun `test simple timeline`() {
    val content = """
    timeline
      title History of Social Media Platform
      2002 : LinkedIn
      2004 : Facebook
           : Google
      2005 : Youtube
      2006 : Twitter
    """.trimIndent()
    doTest(content)
  }

  fun `test complex timeline`() {
    val content = """
    timeline
      title Timeline of Industrial Revolution
      section 17th-20th century
        Industry 1.0 : Machinery, Water power, Steam <br>power
        Industry 2.0 : Electricity, Internal combustion engine, Mass production
        Industry 3.0 : Electronics, Computers, Automation
      section 21st century
        Industry 4.0 : Internet, Robotics, Internet of Things
        Industry 5.0 : Artificial intelligence, Big data,3D printing
    """.trimIndent()
    doTest(content)
  }

  fun `test with ignored tokens`() {
    val content = """
    timeline
      title Timeline of Industrial Rev#olution
      section 17th-20th #century
        Industry 1.0 : Machinery, Water power#, Steam <br>power
        Industry 2.0 : Electricity, Internal combustion engine
                     #: Mass production
        Industry 3.0 #: Electronics, Computers, Automation
      section 21st century
        #Industry 4.0 : Internet, Robotics, Internet of Things
        Industry 5.0 : Artificial intelligence, Big data,3D printing
    """.trimIndent()
    doTest(content)
  }

}
