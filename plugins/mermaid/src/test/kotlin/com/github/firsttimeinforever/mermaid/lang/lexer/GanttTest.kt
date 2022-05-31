package com.github.firsttimeinforever.mermaid.lang.lexer

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COLON
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COMMA
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COMMENT_TEXT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EOL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Gantt.DATE_FORMAT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Gantt.EXCLUDES
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Gantt.GANTT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.LINE_COMMENT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.SECTION
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.SECTION_TITLE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.TASK_DATA
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.TASK_NAME
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.TITLE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.TITLE_VALUE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.WHITE_SPACE

class GanttTest : MermaidLexerTestCase() {
  fun `test simple gantt`() {
    val content = """
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
    val expected = listOf(
      Token(GANTT, 0, 5, "gantt"),
      Token(EOL, 5, 6, "\n"),
      Token(WHITE_SPACE, 6, 8, "  "),
      Token(TITLE, 8, 13, "title"),
      Token(WHITE_SPACE, 13, 14, " "),
      Token(TITLE_VALUE, 14, 29, "A Gantt Diagram"),
      Token(EOL, 29, 30, "\n"),
      Token(WHITE_SPACE, 30, 32, "  "),
      Token(DATE_FORMAT, 32, 54, "dateFormat  YYYY-MM-DD"),
      Token(EOL, 54, 55, "\n"),
      Token(WHITE_SPACE, 55, 57, "  "),
      Token(SECTION, 57, 64, "section"),
      Token(WHITE_SPACE, 64, 65, " "),
      Token(SECTION_TITLE, 65, 72, "Section"),
      Token(EOL, 72, 73, "\n"),
      Token(WHITE_SPACE, 73, 75, "  "),
      Token(TASK_NAME, 75, 92, "A task           "),
      Token(COLON, 92, 93, ":"),
      Token(TASK_DATA, 93, 95, "a1"),
      Token(COMMA, 95, 96, ","),
      Token(TASK_DATA, 96, 107, " 2014-01-01"),
      Token(COMMA, 107, 108, ","),
      Token(TASK_DATA, 108, 112, " 30d"),
      Token(EOL, 112, 113, "\n"),
      Token(WHITE_SPACE, 113, 115, "  "),
      Token(TASK_NAME, 115, 132, "Another task     "),
      Token(COLON, 132, 133, ":"),
      Token(TASK_DATA, 133, 143, "after a1  "),
      Token(COMMA, 143, 144, ","),
      Token(TASK_DATA, 144, 148, " 20d"),
      Token(EOL, 148, 149, "\n"),
      Token(WHITE_SPACE, 149, 151, "  "),
      Token(SECTION, 151, 158, "section"),
      Token(WHITE_SPACE, 158, 159, " "),
      Token(SECTION_TITLE, 159, 166, "Another"),
      Token(EOL, 166, 167, "\n"),
      Token(WHITE_SPACE, 167, 169, "  "),
      Token(TASK_NAME, 169, 186, "Task in sec      "),
      Token(COLON, 186, 187, ":"),
      Token(TASK_DATA, 187, 199, "2014-01-12  "),
      Token(COMMA, 199, 200, ","),
      Token(TASK_DATA, 200, 204, " 12d"),
      Token(EOL, 204, 205, "\n"),
      Token(WHITE_SPACE, 205, 207, "  "),
      Token(TASK_NAME, 207, 225, "another task      "),
      Token(COLON, 225, 226, ":"),
      Token(TASK_DATA, 226, 230, " 24d")
    )
    doTest(content, expected)
  }

  fun `test full complex`() {
    val content = """
    gantt
      dateFormat  YYYY-MM-DD
      title       Adding GANTT diagram functionality to mermaid
      excludes    weekends
      %% (`excludes` accepts specific dates in YYYY-MM-DD format, days of the week ("sunday") or "weekends", but not the word "weekdays".)
  
      section A section
      Completed task            :done,    des1, 2014-01-06,2014-01-08
      Active task               :active,  des2, 2014-01-09, 3d
      Future task               :         des3, after des2, 5d
      Future task2              :         des4, after des3, 5d
  
      section Critical tasks
      Completed task in the critical line :crit, done, 2014-01-06,24h
      Implement parser and jison          :crit, done, after des1, 2d
      Create tests for parser             :crit, active, 3d
      Future task in critical line        :crit, 5d
      Create tests for renderer           :2d
      Add to mermaid                      :1d
      Functionality added                 :milestone, 2014-01-25, 0d
  
      section Documentation
      Describe gantt syntax               :active, a1, after des1, 3d
      Add gantt diagram to demo page      :after a1  , 20h
      Add another diagram to demo page    :doc1, after a1  , 48h
  
      section Last section
      Describe gantt syntax               :after doc1, 3d
      Add gantt diagram to demo page      :20h
      Add another diagram to demo page    :48h
    """.trimIndent()
    val expected = listOf(
      Token(GANTT, 0, 5, "gantt"),
      Token(EOL, 5, 6, "\n"),
      Token(WHITE_SPACE, 6, 8, "  "),
      Token(DATE_FORMAT, 8, 30, "dateFormat  YYYY-MM-DD"),
      Token(EOL, 30, 31, "\n"),
      Token(WHITE_SPACE, 31, 33, "  "),
      Token(TITLE, 33, 38, "title"),
      Token(WHITE_SPACE, 38, 45, "       "),
      Token(TITLE_VALUE, 45, 90, "Adding GANTT diagram functionality to mermaid"),
      Token(EOL, 90, 91, "\n"),
      Token(WHITE_SPACE, 91, 93, "  "),
      Token(EXCLUDES, 93, 113, "excludes    weekends"),
      Token(EOL, 113, 114, "\n"),
      Token(WHITE_SPACE, 114, 116, "  "),
      Token(LINE_COMMENT, 116, 118, "%%"),
      Token(
        COMMENT_TEXT,
        118,
        248,
        " (`excludes` accepts specific dates in YYYY-MM-DD format, days of the week (\"sunday\") or \"weekends\", but not the word \"weekdays\".)"
      ),
      Token(EOL, 248, 250, "\n"),
      Token(WHITE_SPACE, 250, 252, "  "),
      Token(SECTION, 252, 259, "section"),
      Token(WHITE_SPACE, 259, 260, " "),
      Token(SECTION_TITLE, 260, 269, "A section"),
      Token(EOL, 269, 270, "\n"),
      Token(WHITE_SPACE, 270, 272, "  "),
      Token(TASK_NAME, 272, 298, "Completed task            "),
      Token(COLON, 298, 299, ":"),
      Token(TASK_DATA, 299, 303, "done"),
      Token(COMMA, 303, 304, ","),
      Token(TASK_DATA, 304, 312, "    des1"),
      Token(COMMA, 312, 313, ","),
      Token(TASK_DATA, 313, 324, " 2014-01-06"),
      Token(COMMA, 324, 325, ","),
      Token(TASK_DATA, 325, 335, "2014-01-08"),
      Token(EOL, 335, 336, "\n"),
      Token(WHITE_SPACE, 336, 338, "  "),
      Token(TASK_NAME, 338, 364, "Active task               "),
      Token(COLON, 364, 365, ":"),
      Token(TASK_DATA, 365, 371, "active"),
      Token(COMMA, 371, 372, ","),
      Token(TASK_DATA, 372, 378, "  des2"),
      Token(COMMA, 378, 379, ","),
      Token(TASK_DATA, 379, 390, " 2014-01-09"),
      Token(COMMA, 390, 391, ","),
      Token(TASK_DATA, 391, 394, " 3d"),
      Token(EOL, 394, 395, "\n"),
      Token(WHITE_SPACE, 395, 397, "  "),
      Token(TASK_NAME, 397, 423, "Future task               "),
      Token(COLON, 423, 424, ":"),
      Token(TASK_DATA, 424, 437, "         des3"),
      Token(COMMA, 437, 438, ","),
      Token(TASK_DATA, 438, 449, " after des2"),
      Token(COMMA, 449, 450, ","),
      Token(TASK_DATA, 450, 453, " 5d"),
      Token(EOL, 453, 454, "\n"),
      Token(WHITE_SPACE, 454, 456, "  "),
      Token(TASK_NAME, 456, 482, "Future task2              "),
      Token(COLON, 482, 483, ":"),
      Token(TASK_DATA, 483, 496, "         des4"),
      Token(COMMA, 496, 497, ","),
      Token(TASK_DATA, 497, 508, " after des3"),
      Token(COMMA, 508, 509, ","),
      Token(TASK_DATA, 509, 512, " 5d"),
      Token(EOL, 512, 513, "\n"),
      Token(EOL, 513, 514, "\n"),
      Token(WHITE_SPACE, 514, 516, "  "),
      Token(SECTION, 516, 523, "section"),
      Token(WHITE_SPACE, 523, 524, " "),
      Token(SECTION_TITLE, 524, 538, "Critical tasks"),
      Token(EOL, 538, 539, "\n"),
      Token(WHITE_SPACE, 539, 541, "  "),
      Token(TASK_NAME, 541, 577, "Completed task in the critical line "),
      Token(COLON, 577, 578, ":"),
      Token(TASK_DATA, 578, 582, "crit"),
      Token(COMMA, 582, 583, ","),
      Token(TASK_DATA, 583, 588, " done"),
      Token(COMMA, 588, 589, ","),
      Token(TASK_DATA, 589, 600, " 2014-01-06"),
      Token(COMMA, 600, 601, ","),
      Token(TASK_DATA, 601, 604, "24h"),
      Token(EOL, 604, 605, "\n"),
      Token(WHITE_SPACE, 605, 607, "  "),
      Token(TASK_NAME, 607, 643, "Implement parser and jison          "),
      Token(COLON, 643, 644, ":"),
      Token(TASK_DATA, 644, 648, "crit"),
      Token(COMMA, 648, 649, ","),
      Token(TASK_DATA, 649, 654, " done"),
      Token(COMMA, 654, 655, ","),
      Token(TASK_DATA, 655, 666, " after des1"),
      Token(COMMA, 666, 667, ","),
      Token(TASK_DATA, 667, 670, " 2d"),
      Token(EOL, 670, 671, "\n"),
      Token(WHITE_SPACE, 671, 673, "  "),
      Token(TASK_NAME, 673, 709, "Create tests for parser             "),
      Token(COLON, 709, 710, ":"),
      Token(TASK_DATA, 710, 714, "crit"),
      Token(COMMA, 714, 715, ","),
      Token(TASK_DATA, 715, 722, " active"),
      Token(COMMA, 722, 723, ","),
      Token(TASK_DATA, 723, 726, " 3d"),
      Token(EOL, 726, 727, "\n"),
      Token(WHITE_SPACE, 727, 729, "  "),
      Token(TASK_NAME, 729, 765, "Future task in critical line        "),
      Token(COLON, 765, 766, ":"),
      Token(TASK_DATA, 766, 770, "crit"),
      Token(COMMA, 770, 771, ","),
      Token(TASK_DATA, 771, 774, " 5d"),
      Token(EOL, 774, 775, "\n"),
      Token(WHITE_SPACE, 775, 777, "  "),
      Token(TASK_NAME, 777, 813, "Create tests for renderer           "),
      Token(COLON, 813, 814, ":"),
      Token(TASK_DATA, 814, 816, "2d"),
      Token(EOL, 816, 817, "\n"),
      Token(WHITE_SPACE, 817, 819, "  "),
      Token(TASK_NAME, 819, 855, "Add to mermaid                      "),
      Token(COLON, 855, 856, ":"),
      Token(TASK_DATA, 856, 858, "1d"),
      Token(EOL, 858, 859, "\n"),
      Token(WHITE_SPACE, 859, 861, "  "),
      Token(TASK_NAME, 861, 897, "Functionality added                 "),
      Token(COLON, 897, 898, ":"),
      Token(TASK_DATA, 898, 907, "milestone"),
      Token(COMMA, 907, 908, ","),
      Token(TASK_DATA, 908, 919, " 2014-01-25"),
      Token(COMMA, 919, 920, ","),
      Token(TASK_DATA, 920, 923, " 0d"),
      Token(EOL, 923, 924, "\n"),
      Token(EOL, 924, 925, "\n"),
      Token(WHITE_SPACE, 925, 927, "  "),
      Token(SECTION, 927, 934, "section"),
      Token(WHITE_SPACE, 934, 935, " "),
      Token(SECTION_TITLE, 935, 948, "Documentation"),
      Token(EOL, 948, 949, "\n"),
      Token(WHITE_SPACE, 949, 951, "  "),
      Token(TASK_NAME, 951, 987, "Describe gantt syntax               "),
      Token(COLON, 987, 988, ":"),
      Token(TASK_DATA, 988, 994, "active"),
      Token(COMMA, 994, 995, ","),
      Token(TASK_DATA, 995, 998, " a1"),
      Token(COMMA, 998, 999, ","),
      Token(TASK_DATA, 999, 1010, " after des1"),
      Token(COMMA, 1010, 1011, ","),
      Token(TASK_DATA, 1011, 1014, " 3d"),
      Token(EOL, 1014, 1015, "\n"),
      Token(WHITE_SPACE, 1015, 1017, "  "),
      Token(TASK_NAME, 1017, 1053, "Add gantt diagram to demo page      "),
      Token(COLON, 1053, 1054, ":"),
      Token(TASK_DATA, 1054, 1064, "after a1  "),
      Token(COMMA, 1064, 1065, ","),
      Token(TASK_DATA, 1065, 1069, " 20h"),
      Token(EOL, 1069, 1070, "\n"),
      Token(WHITE_SPACE, 1070, 1072, "  "),
      Token(TASK_NAME, 1072, 1108, "Add another diagram to demo page    "),
      Token(COLON, 1108, 1109, ":"),
      Token(TASK_DATA, 1109, 1113, "doc1"),
      Token(COMMA, 1113, 1114, ","),
      Token(TASK_DATA, 1114, 1125, " after a1  "),
      Token(COMMA, 1125, 1126, ","),
      Token(TASK_DATA, 1126, 1130, " 48h"),
      Token(EOL, 1130, 1131, "\n"),
      Token(EOL, 1131, 1132, "\n"),
      Token(WHITE_SPACE, 1132, 1134, "  "),
      Token(SECTION, 1134, 1141, "section"),
      Token(WHITE_SPACE, 1141, 1142, " "),
      Token(SECTION_TITLE, 1142, 1154, "Last section"),
      Token(EOL, 1154, 1155, "\n"),
      Token(WHITE_SPACE, 1155, 1157, "  "),
      Token(TASK_NAME, 1157, 1193, "Describe gantt syntax               "),
      Token(COLON, 1193, 1194, ":"),
      Token(TASK_DATA, 1194, 1204, "after doc1"),
      Token(COMMA, 1204, 1205, ","),
      Token(TASK_DATA, 1205, 1208, " 3d"),
      Token(EOL, 1208, 1209, "\n"),
      Token(WHITE_SPACE, 1209, 1211, "  "),
      Token(TASK_NAME, 1211, 1247, "Add gantt diagram to demo page      "),
      Token(COLON, 1247, 1248, ":"),
      Token(TASK_DATA, 1248, 1251, "20h"),
      Token(EOL, 1251, 1252, "\n"),
      Token(WHITE_SPACE, 1252, 1254, "  "),
      Token(TASK_NAME, 1254, 1290, "Add another diagram to demo page    "),
      Token(COLON, 1290, 1291, ":"),
      Token(TASK_DATA, 1291, 1294, "48h")
    )
    doTest(content, expected)
  }
}
