package com.intellij.mermaid.api

external interface MermaidConfig {
  val theme: String?
  // val themeVariables: Any?
  // @JsName("themeCSS")
  // val themeStyles: String?
  // val maxTextSize: Number?
  val darkMode: Boolean?
  // val htmlLabels: Boolean?
  val fontFamily: String?
  val altFontFamily: String?
  // val logLevel: Number?
  // val securityLevel: String
  val startOnLoad: Boolean?
  // val arrowMarkerAbsolute: Boolean?
  // val secure: Array<String>?
  // val deterministicIds: Boolean?
  // val deterministicIDSeed: String?
  val wrap: Boolean?
  val fontSize: Number?
  // flowchart?: FlowchartDiagramConfig
  // sequence?: SequenceDiagramConfig
  // gantt?: GanttDiagramConfig
  // journey?: JourneyDiagramConfig
  // `class`?: ClassDiagramConfig
  // state?: StateDiagramConfig
  // er?: ErDiagramConfig
  // pie?: PieDiagramConfig
  // requirement?: RequirementDiagramConfig
  // mindmap?: MindmapDiagramConfig
  // gitGraph?: GitGraphDiagramConfig
  // c4?: C4DiagramConfig
  // dompurifyConfig?: DOMPurify.Config
}
