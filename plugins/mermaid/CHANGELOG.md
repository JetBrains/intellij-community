# Mermaid Changelog

## [Unreleased]

## 0.0.13
- [MERMAID-67](https://youtrack.jetbrains.com/issue/MERMAID-67) Fixed parsing of signals surrounded by spaces in sequence diagrams
- [MERMAID-69](https://youtrack.jetbrains.com/issue/MERMAID-69) Added support for Markdown strings inside flowchart and mindmap labels
- [MERMAID-70](https://youtrack.jetbrains.com/issue/MERMAID-70) Fixed parsing of shapes with slashes in flowcharts
- [MERMAID-73](https://youtrack.jetbrains.com/issue/MERMAID-73) Fixed parsing of colon characters in class diagrams
- [MERMAID-75](https://youtrack.jetbrains.com/issue/MERMAID-75) Fixed parsing of title statements in sequence diagrams
- [MERMAID-77](https://youtrack.jetbrains.com/issue/MERMAID-77) Added support for quadrant charts
- [MERMAID-78](https://youtrack.jetbrains.com/issue/MERMAID-78) Added support for hyphens inside class diagram identifiers
- [MERMAID-79](https://youtrack.jetbrains.com/issue/MERMAID-79) Added support for namespaces in class diagrams
- [MERMAID-81](https://youtrack.jetbrains.com/issue/MERMAID-81) Add Markdown language injections to Markdown strings 

## 0.0.12
- [MERMAID-56](https://youtrack.jetbrains.com/issue/MERMAID-56) Fixed freezes during diagram directive editing

## 0.0.11
- [MERMAID-21](https://youtrack.jetbrains.com/issue/MERMAID-21) Fixed completion after front matter header
- [MERMAID-34](https://youtrack.jetbrains.com/issue/MERMAID-34) Added more highlightable tokens for the syntax highlighter
- [MERMAID-45](https://youtrack.jetbrains.com/issue/MERMAID-45) Fixed incorrect diagram height
- [MERMAID-49](https://youtrack.jetbrains.com/issue/MERMAID-49) Fixed handling of empty diagrams in the preview
- [MERMAID-54](https://youtrack.jetbrains.com/issue/MERMAID-54) Fixed preview theme updates on global theme change

## 0.0.10
- Updated an underlying Mermaid.js version to `10.0.2`
- [MERMAID-24](https://youtrack.jetbrains.com/issue/MERMAID-24) Added diagram preview for standalone Mermaid files (`.mmd` and `.mermaid`)
- [MERMAID-28](https://youtrack.jetbrains.com/issue/MERMAID-28) Added support for timeline diagrams
- [MERMAID-31](https://youtrack.jetbrains.com/issue/MERMAID-31) Fixed cropped pie diagrams

## 0.0.9
- [MERMAID-26](https://youtrack.jetbrains.com/issue/MERMAID-26) Added support for Mermaid diagram files with `.mmd` extension
- [MERMAID-27](https://youtrack.jetbrains.com/issue/MERMAID-27) Updated an underlying Mermaid.js version to `9.4.0`
- Added partial support for timeline diagrams

## 0.0.8
- [MERMAID-2](https://youtrack.jetbrains.com/issue/MERMAID-2) Added rendering of parsing errors in diagram preview
- [MERMAID-9](https://youtrack.jetbrains.com/issue/MERMAID-9) Fixed rendering of non-existent icons in Mindmap diagram
- [MERMAID-14](https://youtrack.jetbrains.com/issue/MERMAID-14) Fixed incorrect diagram rendering when multiple diagrams are present on the page
- [MERMAID-15](https://youtrack.jetbrains.com/issue/MERMAID-15) Fixed incorrect scaling of cached diagrams
- [MERMAID-21](https://youtrack.jetbrains.com/issue/MERMAID-21) Fixed keyword completion inside class diagrams
- Added Mermaid settings page

## 0.0.7
- Fix leaking enter handler inside braces

## 0.0.6
- Added support for mind map diagrams
- Added partial support for Mermaid.js `9.3.0`
- [IDEA-306720](https://youtrack.jetbrains.com/issue/IDEA-306720) Fixed incorrect caret position after pressing enter in class diagrams
- [IDEA-307784](https://youtrack.jetbrains.com/issue/IDEA-307784) Support graph keyword for flowcharts
- [IDEA-307891](https://youtrack.jetbrains.com/issue/IDEA-307891) Fixed parsing of object arrays in class diagrams

## 0.0.5
- [IDEA-306721](https://youtrack.jetbrains.com/issue/IDEA-306721) Fixed lexer non-progressing in some cases
- Updated plugin description

## 0.0.3-nightly
- Updated Mermaid.js version to 9.2.0
- Added integration with [Markdown](https://plugins.jetbrains.com/plugin/7793-markdown) plugin
- Updated plugin icon and description

## 0.0.2-nightly
- This is maintenance nightly release
- Added support for git graph diagram
- Internal fixes and optimizations

## 0.0.1
- Added Mermaid v.9.1.7 support
