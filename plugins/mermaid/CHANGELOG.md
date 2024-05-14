# Mermaid Changelog

## [Unreleased]

## 0.0.22
- Updated Mermaid.js version to `10.9.1`

## 0.0.21
- Updated Mermaid.js version to `10.9.0`

## 0.0.20
- Updated Mermaid.js version to `10.8.0`
- [MERMAID-163](https://youtrack.jetbrains.com/issue/MERMAID-163) Updated GitGraph parser to allow cherry-picking of merge commit 
- [MERMAID-165](https://youtrack.jetbrains.com/issue/MERMAID-165) Added `style` keyword support for class diagram
- [MERMAID-153](https://youtrack.jetbrains.com/issue/MERMAID-153) Updated xyChart configuration
- [MERMAID-171](https://youtrack.jetbrains.com/issue/MERMAID-171) Updated Flowchart configuration
- [MERMAID-169](https://youtrack.jetbrains.com/issue/MERMAID-169) Updated GitGraph configuration
- [MERMAID-170](https://youtrack.jetbrains.com/issue/MERMAID-170) Updated Gantt parser to allow usage of hash and semicolon in the title, section, and task title
- [MERMAID-168](https://youtrack.jetbrains.com/issue/MERMAID-168) Added support for Block Diagram
- [MERMAID-150](https://youtrack.jetbrains.com/issue/MERMAID-150) Fixed parsing of asynchronous messages with activation in sequence diagram

## 0.0.19
- [MERMAID-161](https://youtrack.jetbrains.com/issue/MERMAID-161) Added an option for configuring additional code fence info string patterns

## 0.0.18
- Updated Mermaid.js version to `10.6.1`
- [MERMAID-146](https://youtrack.jetbrains.com/issue/MERMAID-146) Added support for XYChart

## 0.0.17
- [MERMAID-149](https://youtrack.jetbrains.com/issue/MERMAID-149) Fixed an issue that prevented the currently opened project from closing properly

## 0.0.16
- [MERMAID-140](https://youtrack.jetbrains.com/issue/MERMAID-140) Updated Mermaid.js version to `10.5.1`
- [MERMAID-141](https://youtrack.jetbrains.com/issue/MERMAID-141) Fixed incorrect parsing of generics with leading and nested tilde characters in class diagrams
- [MERMAID-142](https://youtrack.jetbrains.com/issue/MERMAID-142) Fixed parsing of leading underscore characters in ER diagrams
- [MERMAID-143](https://youtrack.jetbrains.com/issue/MERMAID-143) Added support for entity alias in ER diagram
- [MERMAID-144](https://youtrack.jetbrains.com/issue/MERMAID-144) Fixed parsing of trailing semicolons in flowchart header and quadrant chart statements

## 0.0.15
- [MERMAID-116](https://youtrack.jetbrains.com/issue/MERMAID-116) Updated Mermaid.js version to `10.4.0`
- [MERMAID-117](https://youtrack.jetbrains.com/issue/MERMAID-117) Added support for defining configuration in front matter header
- [MERMAID-118](https://youtrack.jetbrains.com/issue/MERMAID-118) Added support for values in sankey diagrams
- [MERMAID-122](https://youtrack.jetbrains.com/issue/MERMAID-122) Fixed parsing error during subgraph formatting
- [MERMAID-123](https://youtrack.jetbrains.com/issue/MERMAID-123) Fixed errors caused by class diagram reformatting
- [MERMAID-126](https://youtrack.jetbrains.com/issue/MERMAID-126) Fixed parsing error during sequence diagram formatting
- [MERMAID-127](https://youtrack.jetbrains.com/issue/MERMAID-127) Fixed formatting of HTML entity codes
- [MERMAID-129](https://youtrack.jetbrains.com/issue/MERMAID-129) Added support for case-insensitive `Note` keyword

## 0.0.14
- Updated an underlying Mermaid.js version to `10.3.1`
- [MERMAID-8](https://youtrack.jetbrains.com/issue/MERMAID-8) Added support for Font Awesome icons in mind map diagrams
- [MERMAID-15](https://youtrack.jetbrains.com/issue/MERMAID-15) Fixed incorrect diagram scaling after restoring from cache
- [MERMAID-30](https://youtrack.jetbrains.com/issue/MERMAID-30) Added support for YAML language injections into diagram front matter
- [MERMAID-33](https://youtrack.jetbrains.com/issue/MERMAID-33) Added JSON schema-based validation for the front matter content
- [MERMAID-58](https://youtrack.jetbrains.com/issue/MERMAID-58) Added completion of triple colon syntax for Mermaid code fences
- [MERMAID-88](https://youtrack.jetbrains.com/issue/MERMAID-88) Added support for JSON language injections into directive blocks
- [MERMAID-89](https://youtrack.jetbrains.com/issue/MERMAID-89) Fixed incorrect formatter behaviour when the diagram has HTML markup in it
- [MERMAID-92](https://youtrack.jetbrains.com/issue/MERMAID-92) Added more highlighting for Gantt diagram keywords  
- [MERMAID-93](https://youtrack.jetbrains.com/issue/MERMAID-93) Added highlighting for the special states in state diagrams 
- [MERMAID-94](https://youtrack.jetbrains.com/issue/MERMAID-94) Added highlighting for data types in class and ER diagrams
- [MERMAID-105](https://youtrack.jetbrains.com/issue/MERMAID-105) Fixed formatting of graph vertices with HTML markup
- [MERMAID-106](https://youtrack.jetbrains.com/issue/MERMAID-106) Fixed Git graph rendering in Markdown files when there are custom styles present in the document 
- [MERMAID-107](https://youtrack.jetbrains.com/issue/MERMAID-107) Fixed incorrect warnings for unresolved branches in Git graph
- [MERMAID-109](https://youtrack.jetbrains.com/issue/MERMAID-109) Added support for keywords inside flowchart nodes

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
