// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.demo

/**
 * Produces a mermaid flowchart describing the (imaginary) IntelliJ WebView
 * product roadmap. Most nodes are static, but the "Live board" subgraph is
 * stamped with the current Backlog/In Progress/Review/Done counts so the
 * diagram visibly updates on every snapshot tick — a cheap demo of Kotlin
 * streaming arbitrary text content into the WebView.
 */
internal object SampleRoadmapMermaid {
  fun build(tasks: List<SampleTask>): String {
    val counts = SampleBoardConstants.STATUSES.associateWith { status ->
      tasks.count { it.status == status }
    }
    val backlog = counts["Backlog"] ?: 0
    val inProgress = counts["In Progress"] ?: 0
    val review = counts["Review"] ?: 0
    val done = counts["Done"] ?: 0

    return """
      graph LR
        ROOT["IntelliJ WebView Roadmap"] --> V1["2026.1 — Bridge MVP"]
        ROOT --> V2["2026.2 — Rich UI"]
        ROOT --> V3["2026.3 — Ecosystem"]
        ROOT --> V4["2026.4+ — GA"]

        V1 --> V1_A["WKWebView shell"]:::done
        V1 --> V1_B["JSON-RPC bus"]:::done
        V1 --> V1_C["Theme sync"]:::done
        V1_B --> V1_B1["typed notify / handlers"]
        V1_B --> V1_B2["lifecycle-owned handlers"]
        V1_A --> V1_A1["resource extraction"]
        V1_A --> V1_A2["WKURLSchemeHandler (next)"]

        V2 --> V2_A["Kanban demo"]:::done
        V2 --> V2_B["Drag & drop"]:::done
        V2 --> V2_C["Release timeline"]:::done
        V2 --> V2_D["Mermaid roadmap"]:::active
        V2 --> V2_E["Inline dialogs"]
        V2_D --> V2_D1["live task counts"]:::active
        V2_D --> V2_D2["theme aware"]

        V3 --> V3_A["Windows backend"]
        V3 --> V3_B["Linux backend"]
        V3 --> V3_C["DevTools integration"]
        V3 --> V3_D["Custom resource protocol"]
        V3_A --> V3_A1["WebView2"]
        V3_B --> V3_B1["WebKitGTK"]

        V4 --> V4_A["Plugin SDK"]
        V4 --> V4_B["Performance budget"]
        V4 --> V4_C["A11y pass"]

        subgraph LIVE["Live board (Kotlin auto-tick)"]
          direction LR
          BK["Backlog: $backlog"] --> IP["In Progress: $inProgress"]
          IP --> RV["Review: $review"]
          RV --> DN["Done: $done"]
        end

        V2_D1 -.-> BK

        classDef active fill:#3574F0,stroke:#1C5BC8,color:#FFFFFF;
        classDef done   fill:#5FAD65,stroke:#3F7E43,color:#FFFFFF;
    """.trimIndent()
  }
}
