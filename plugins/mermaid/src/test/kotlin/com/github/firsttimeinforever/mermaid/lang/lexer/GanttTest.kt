package com.github.firsttimeinforever.mermaid.lang.lexer

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CALL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CLICK
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CLICK_DATA
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CLOSE_ROUND
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COLON
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COMMA
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DOUBLE_QUOTE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EOL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Gantt.DATE_FORMAT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Gantt.EXCLUDES
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Gantt.GANTT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Gantt.TODAY_MARKER
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.HREF
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.LINE_COMMENT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.OPEN_ROUND
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.SECTION
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.SECTION_TITLE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.STRING_VALUE
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
      Token(TASK_NAME, 75, 76, "A"),
      Token(WHITE_SPACE, 76, 77, " "),
      Token(TASK_NAME, 77, 81, "task"),
      Token(WHITE_SPACE, 81, 92, "           "),
      Token(COLON, 92, 93, ":"),
      Token(TASK_DATA, 93, 95, "a1"),
      Token(COMMA, 95, 96, ","),
      Token(TASK_DATA, 96, 107, " 2014-01-01"),
      Token(COMMA, 107, 108, ","),
      Token(TASK_DATA, 108, 112, " 30d"),
      Token(EOL, 112, 113, "\n"),
      Token(WHITE_SPACE, 113, 115, "  "),
      Token(TASK_NAME, 115, 122, "Another"),
      Token(WHITE_SPACE, 122, 123, " "),
      Token(TASK_NAME, 123, 127, "task"),
      Token(WHITE_SPACE, 127, 132, "     "),
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
      Token(TASK_NAME, 169, 173, "Task"),
      Token(WHITE_SPACE, 173, 174, " "),
      Token(TASK_NAME, 174, 176, "in"),
      Token(WHITE_SPACE, 176, 177, " "),
      Token(TASK_NAME, 177, 180, "sec"),
      Token(WHITE_SPACE, 180, 186, "      "),
      Token(COLON, 186, 187, ":"),
      Token(TASK_DATA, 187, 199, "2014-01-12  "),
      Token(COMMA, 199, 200, ","),
      Token(TASK_DATA, 200, 204, " 12d"),
      Token(EOL, 204, 205, "\n"),
      Token(WHITE_SPACE, 205, 207, "  "),
      Token(TASK_NAME, 207, 214, "another"),
      Token(WHITE_SPACE, 214, 215, " "),
      Token(TASK_NAME, 215, 219, "task"),
      Token(WHITE_SPACE, 219, 225, "      "),
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
      Token(
        LINE_COMMENT,
        116,
        248,
        "%% (`excludes` accepts specific dates in YYYY-MM-DD format, days of the week (\"sunday\") or \"weekends\", but not the word \"weekdays\".)"
      ),
      Token(EOL, 248, 249, "\n"),
      Token(EOL, 249, 250, "\n"),
      Token(WHITE_SPACE, 250, 252, "  "),
      Token(SECTION, 252, 259, "section"),
      Token(WHITE_SPACE, 259, 260, " "),
      Token(SECTION_TITLE, 260, 269, "A section"),
      Token(EOL, 269, 270, "\n"),
      Token(WHITE_SPACE, 270, 272, "  "),
      Token(TASK_NAME, 272, 281, "Completed"),
      Token(WHITE_SPACE, 281, 282, " "),
      Token(TASK_NAME, 282, 286, "task"),
      Token(WHITE_SPACE, 286, 298, "            "),
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
      Token(TASK_NAME, 338, 344, "Active"),
      Token(WHITE_SPACE, 344, 345, " "),
      Token(TASK_NAME, 345, 349, "task"),
      Token(WHITE_SPACE, 349, 364, "               "),
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
      Token(TASK_NAME, 397, 403, "Future"),
      Token(WHITE_SPACE, 403, 404, " "),
      Token(TASK_NAME, 404, 408, "task"),
      Token(WHITE_SPACE, 408, 423, "               "),
      Token(COLON, 423, 424, ":"),
      Token(TASK_DATA, 424, 437, "         des3"),
      Token(COMMA, 437, 438, ","),
      Token(TASK_DATA, 438, 449, " after des2"),
      Token(COMMA, 449, 450, ","),
      Token(TASK_DATA, 450, 453, " 5d"),
      Token(EOL, 453, 454, "\n"),
      Token(WHITE_SPACE, 454, 456, "  "),
      Token(TASK_NAME, 456, 462, "Future"),
      Token(WHITE_SPACE, 462, 463, " "),
      Token(TASK_NAME, 463, 468, "task2"),
      Token(WHITE_SPACE, 468, 482, "              "),
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
      Token(TASK_NAME, 541, 550, "Completed"),
      Token(WHITE_SPACE, 550, 551, " "),
      Token(TASK_NAME, 551, 555, "task"),
      Token(WHITE_SPACE, 555, 556, " "),
      Token(TASK_NAME, 556, 558, "in"),
      Token(WHITE_SPACE, 558, 559, " "),
      Token(TASK_NAME, 559, 562, "the"),
      Token(WHITE_SPACE, 562, 563, " "),
      Token(TASK_NAME, 563, 571, "critical"),
      Token(WHITE_SPACE, 571, 572, " "),
      Token(TASK_NAME, 572, 576, "line"),
      Token(WHITE_SPACE, 576, 577, " "),
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
      Token(TASK_NAME, 607, 616, "Implement"),
      Token(WHITE_SPACE, 616, 617, " "),
      Token(TASK_NAME, 617, 623, "parser"),
      Token(WHITE_SPACE, 623, 624, " "),
      Token(TASK_NAME, 624, 627, "and"),
      Token(WHITE_SPACE, 627, 628, " "),
      Token(TASK_NAME, 628, 633, "jison"),
      Token(WHITE_SPACE, 633, 643, "          "),
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
      Token(TASK_NAME, 673, 679, "Create"),
      Token(WHITE_SPACE, 679, 680, " "),
      Token(TASK_NAME, 680, 685, "tests"),
      Token(WHITE_SPACE, 685, 686, " "),
      Token(TASK_NAME, 686, 689, "for"),
      Token(WHITE_SPACE, 689, 690, " "),
      Token(TASK_NAME, 690, 696, "parser"),
      Token(WHITE_SPACE, 696, 709, "             "),
      Token(COLON, 709, 710, ":"),
      Token(TASK_DATA, 710, 714, "crit"),
      Token(COMMA, 714, 715, ","),
      Token(TASK_DATA, 715, 722, " active"),
      Token(COMMA, 722, 723, ","),
      Token(TASK_DATA, 723, 726, " 3d"),
      Token(EOL, 726, 727, "\n"),
      Token(WHITE_SPACE, 727, 729, "  "),
      Token(TASK_NAME, 729, 735, "Future"),
      Token(WHITE_SPACE, 735, 736, " "),
      Token(TASK_NAME, 736, 740, "task"),
      Token(WHITE_SPACE, 740, 741, " "),
      Token(TASK_NAME, 741, 743, "in"),
      Token(WHITE_SPACE, 743, 744, " "),
      Token(TASK_NAME, 744, 752, "critical"),
      Token(WHITE_SPACE, 752, 753, " "),
      Token(TASK_NAME, 753, 757, "line"),
      Token(WHITE_SPACE, 757, 765, "        "),
      Token(COLON, 765, 766, ":"),
      Token(TASK_DATA, 766, 770, "crit"),
      Token(COMMA, 770, 771, ","),
      Token(TASK_DATA, 771, 774, " 5d"),
      Token(EOL, 774, 775, "\n"),
      Token(WHITE_SPACE, 775, 777, "  "),
      Token(TASK_NAME, 777, 783, "Create"),
      Token(WHITE_SPACE, 783, 784, " "),
      Token(TASK_NAME, 784, 789, "tests"),
      Token(WHITE_SPACE, 789, 790, " "),
      Token(TASK_NAME, 790, 793, "for"),
      Token(WHITE_SPACE, 793, 794, " "),
      Token(TASK_NAME, 794, 802, "renderer"),
      Token(WHITE_SPACE, 802, 813, "           "),
      Token(COLON, 813, 814, ":"),
      Token(TASK_DATA, 814, 816, "2d"),
      Token(EOL, 816, 817, "\n"),
      Token(WHITE_SPACE, 817, 819, "  "),
      Token(TASK_NAME, 819, 822, "Add"),
      Token(WHITE_SPACE, 822, 823, " "),
      Token(TASK_NAME, 823, 825, "to"),
      Token(WHITE_SPACE, 825, 826, " "),
      Token(TASK_NAME, 826, 833, "mermaid"),
      Token(WHITE_SPACE, 833, 855, "                      "),
      Token(COLON, 855, 856, ":"),
      Token(TASK_DATA, 856, 858, "1d"),
      Token(EOL, 858, 859, "\n"),
      Token(WHITE_SPACE, 859, 861, "  "),
      Token(TASK_NAME, 861, 874, "Functionality"),
      Token(WHITE_SPACE, 874, 875, " "),
      Token(TASK_NAME, 875, 880, "added"),
      Token(WHITE_SPACE, 880, 897, "                 "),
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
      Token(TASK_NAME, 951, 959, "Describe"),
      Token(WHITE_SPACE, 959, 960, " "),
      Token(TASK_NAME, 960, 965, "gantt"),
      Token(WHITE_SPACE, 965, 966, " "),
      Token(TASK_NAME, 966, 972, "syntax"),
      Token(WHITE_SPACE, 972, 987, "               "),
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
      Token(TASK_NAME, 1017, 1020, "Add"),
      Token(WHITE_SPACE, 1020, 1021, " "),
      Token(TASK_NAME, 1021, 1026, "gantt"),
      Token(WHITE_SPACE, 1026, 1027, " "),
      Token(TASK_NAME, 1027, 1034, "diagram"),
      Token(WHITE_SPACE, 1034, 1035, " "),
      Token(TASK_NAME, 1035, 1037, "to"),
      Token(WHITE_SPACE, 1037, 1038, " "),
      Token(TASK_NAME, 1038, 1042, "demo"),
      Token(WHITE_SPACE, 1042, 1043, " "),
      Token(TASK_NAME, 1043, 1047, "page"),
      Token(WHITE_SPACE, 1047, 1053, "      "),
      Token(COLON, 1053, 1054, ":"),
      Token(TASK_DATA, 1054, 1064, "after a1  "),
      Token(COMMA, 1064, 1065, ","),
      Token(TASK_DATA, 1065, 1069, " 20h"),
      Token(EOL, 1069, 1070, "\n"),
      Token(WHITE_SPACE, 1070, 1072, "  "),
      Token(TASK_NAME, 1072, 1075, "Add"),
      Token(WHITE_SPACE, 1075, 1076, " "),
      Token(TASK_NAME, 1076, 1083, "another"),
      Token(WHITE_SPACE, 1083, 1084, " "),
      Token(TASK_NAME, 1084, 1091, "diagram"),
      Token(WHITE_SPACE, 1091, 1092, " "),
      Token(TASK_NAME, 1092, 1094, "to"),
      Token(WHITE_SPACE, 1094, 1095, " "),
      Token(TASK_NAME, 1095, 1099, "demo"),
      Token(WHITE_SPACE, 1099, 1100, " "),
      Token(TASK_NAME, 1100, 1104, "page"),
      Token(WHITE_SPACE, 1104, 1108, "    "),
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
      Token(TASK_NAME, 1157, 1165, "Describe"),
      Token(WHITE_SPACE, 1165, 1166, " "),
      Token(TASK_NAME, 1166, 1171, "gantt"),
      Token(WHITE_SPACE, 1171, 1172, " "),
      Token(TASK_NAME, 1172, 1178, "syntax"),
      Token(WHITE_SPACE, 1178, 1193, "               "),
      Token(COLON, 1193, 1194, ":"),
      Token(TASK_DATA, 1194, 1204, "after doc1"),
      Token(COMMA, 1204, 1205, ","),
      Token(TASK_DATA, 1205, 1208, " 3d"),
      Token(EOL, 1208, 1209, "\n"),
      Token(WHITE_SPACE, 1209, 1211, "  "),
      Token(TASK_NAME, 1211, 1214, "Add"),
      Token(WHITE_SPACE, 1214, 1215, " "),
      Token(TASK_NAME, 1215, 1220, "gantt"),
      Token(WHITE_SPACE, 1220, 1221, " "),
      Token(TASK_NAME, 1221, 1228, "diagram"),
      Token(WHITE_SPACE, 1228, 1229, " "),
      Token(TASK_NAME, 1229, 1231, "to"),
      Token(WHITE_SPACE, 1231, 1232, " "),
      Token(TASK_NAME, 1232, 1236, "demo"),
      Token(WHITE_SPACE, 1236, 1237, " "),
      Token(TASK_NAME, 1237, 1241, "page"),
      Token(WHITE_SPACE, 1241, 1247, "      "),
      Token(COLON, 1247, 1248, ":"),
      Token(TASK_DATA, 1248, 1251, "20h"),
      Token(EOL, 1251, 1252, "\n"),
      Token(WHITE_SPACE, 1252, 1254, "  "),
      Token(TASK_NAME, 1254, 1257, "Add"),
      Token(WHITE_SPACE, 1257, 1258, " "),
      Token(TASK_NAME, 1258, 1265, "another"),
      Token(WHITE_SPACE, 1265, 1266, " "),
      Token(TASK_NAME, 1266, 1273, "diagram"),
      Token(WHITE_SPACE, 1273, 1274, " "),
      Token(TASK_NAME, 1274, 1276, "to"),
      Token(WHITE_SPACE, 1276, 1277, " "),
      Token(TASK_NAME, 1277, 1281, "demo"),
      Token(WHITE_SPACE, 1281, 1282, " "),
      Token(TASK_NAME, 1282, 1286, "page"),
      Token(WHITE_SPACE, 1286, 1290, "    "),
      Token(COLON, 1290, 1291, ":"),
      Token(TASK_DATA, 1291, 1294, "48h")
    )
    doTest(content, expected)
  }

  fun `test click statements`() {
    val content = """
    gantt
      Visit mermaidjs :active, cl1, 2014-01-07, 3d
      Print arguments :cl2, after cl1, 3d
      Print task :cl3, after cl2, 3d

      click cl1 href "https://mermaidjs.github.io/"
      click cl2 call printArguments("test1", "test2", test3)
      click cl3 call printTask()
    """.trimIndent()
    val expected = listOf(
      Token(GANTT, 0, 5, "gantt"),
      Token(EOL, 5, 6, "\n"),
      Token(WHITE_SPACE, 6, 8, "  "),
      Token(TASK_NAME, 8, 13, "Visit"),
      Token(WHITE_SPACE, 13, 14, " "),
      Token(TASK_NAME, 14, 23, "mermaidjs"),
      Token(WHITE_SPACE, 23, 24, " "),
      Token(COLON, 24, 25, ":"),
      Token(TASK_DATA, 25, 31, "active"),
      Token(COMMA, 31, 32, ","),
      Token(TASK_DATA, 32, 36, " cl1"),
      Token(COMMA, 36, 37, ","),
      Token(TASK_DATA, 37, 48, " 2014-01-07"),
      Token(COMMA, 48, 49, ","),
      Token(TASK_DATA, 49, 52, " 3d"),
      Token(EOL, 52, 53, "\n"),
      Token(WHITE_SPACE, 53, 55, "  "),
      Token(TASK_NAME, 55, 60, "Print"),
      Token(WHITE_SPACE, 60, 61, " "),
      Token(TASK_NAME, 61, 70, "arguments"),
      Token(WHITE_SPACE, 70, 71, " "),
      Token(COLON, 71, 72, ":"),
      Token(TASK_DATA, 72, 75, "cl2"),
      Token(COMMA, 75, 76, ","),
      Token(TASK_DATA, 76, 86, " after cl1"),
      Token(COMMA, 86, 87, ","),
      Token(TASK_DATA, 87, 90, " 3d"),
      Token(EOL, 90, 91, "\n"),
      Token(WHITE_SPACE, 91, 93, "  "),
      Token(TASK_NAME, 93, 98, "Print"),
      Token(WHITE_SPACE, 98, 99, " "),
      Token(TASK_NAME, 99, 103, "task"),
      Token(WHITE_SPACE, 103, 104, " "),
      Token(COLON, 104, 105, ":"),
      Token(TASK_DATA, 105, 108, "cl3"),
      Token(COMMA, 108, 109, ","),
      Token(TASK_DATA, 109, 119, " after cl2"),
      Token(COMMA, 119, 120, ","),
      Token(TASK_DATA, 120, 123, " 3d"),
      Token(EOL, 123, 124, "\n"),
      Token(EOL, 124, 125, "\n"),
      Token(WHITE_SPACE, 125, 127, "  "),
      Token(CLICK, 127, 132, "click"),
      Token(WHITE_SPACE, 132, 133, " "),
      Token(CLICK_DATA, 133, 136, "cl1"),
      Token(WHITE_SPACE, 136, 137, " "),
      Token(HREF, 137, 141, "href"),
      Token(WHITE_SPACE, 141, 142, " "),
      Token(DOUBLE_QUOTE, 142, 143, "\""),
      Token(STRING_VALUE, 143, 171, "https://mermaidjs.github.io/"),
      Token(DOUBLE_QUOTE, 171, 172, "\""),
      Token(EOL, 172, 173, "\n"),
      Token(WHITE_SPACE, 173, 175, "  "),
      Token(CLICK, 175, 180, "click"),
      Token(WHITE_SPACE, 180, 181, " "),
      Token(CLICK_DATA, 181, 184, "cl2"),
      Token(WHITE_SPACE, 184, 185, " "),
      Token(CALL, 185, 189, "call"),
      Token(WHITE_SPACE, 189, 190, " "),
      Token(CLICK_DATA, 190, 204, "printArguments"),
      Token(OPEN_ROUND, 204, 205, "("),
      Token(DOUBLE_QUOTE, 205, 206, "\""),
      Token(STRING_VALUE, 206, 211, "test1"),
      Token(DOUBLE_QUOTE, 211, 212, "\""),
      Token(COMMA, 212, 213, ","),
      Token(WHITE_SPACE, 213, 214, " "),
      Token(DOUBLE_QUOTE, 214, 215, "\""),
      Token(STRING_VALUE, 215, 220, "test2"),
      Token(DOUBLE_QUOTE, 220, 221, "\""),
      Token(COMMA, 221, 222, ","),
      Token(WHITE_SPACE, 222, 223, " "),
      Token(CLICK_DATA, 223, 228, "test3"),
      Token(CLOSE_ROUND, 228, 229, ")"),
      Token(EOL, 229, 230, "\n"),
      Token(WHITE_SPACE, 230, 232, "  "),
      Token(CLICK, 232, 237, "click"),
      Token(WHITE_SPACE, 237, 238, " "),
      Token(CLICK_DATA, 238, 241, "cl3"),
      Token(WHITE_SPACE, 241, 242, " "),
      Token(CALL, 242, 246, "call"),
      Token(WHITE_SPACE, 246, 247, " "),
      Token(CLICK_DATA, 247, 256, "printTask"),
      Token(OPEN_ROUND, 256, 257, "("),
      Token(CLOSE_ROUND, 257, 258, ")")
    )
    doTest(content, expected)
  }

  fun `test today marker`() {
    val content = """
    gantt
      todayMarker off
      todayMarker stroke-width:5px,stroke:#0f0,opacity:0.5
    """.trimIndent()
    val expected = listOf(
      Token(GANTT, 0, 5, "gantt"),
      Token(EOL, 5, 6, "\n"),
      Token(WHITE_SPACE, 6, 8, "  "),
      Token(TODAY_MARKER, 8, 23, "todayMarker off"),
      Token(EOL, 23, 24, "\n"),
      Token(WHITE_SPACE, 24, 26, "  "),
      Token(TODAY_MARKER, 26, 78, "todayMarker stroke-width:5px,stroke:#0f0,opacity:0.5")
    )
    doTest(content, expected)
  }
}
