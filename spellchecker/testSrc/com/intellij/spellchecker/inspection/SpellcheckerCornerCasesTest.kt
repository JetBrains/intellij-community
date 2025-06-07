// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.inspection

import com.intellij.spellchecker.SpellCheckerManager


class SpellcheckerCornerCasesTest : SpellcheckerInspectionTestCase() {
  fun `test a lot of mistakes in united word suggest`() {
    //should not end up with OOM
    val manager = SpellCheckerManager.getInstance(project)
    val suggestions = manager.getSuggestions("MYY_VERRY_LOOONG_WORDD_WOTH_A_LOTTT_OFFF_MISAKES")
    assertTrue(suggestions.isNotEmpty())
  }

  fun `test that korean language is treated as alien`() {
    myFixture.enableInspections(*getInspectionTools())
    myFixture.configureByText("a.txt", """
        <TYPO descr="Typo: In word 'loadd'">loadd</TYPO>이벤트가
        이벤트가<TYPO descr="Typo: In word 'loadd'">loadd</TYPO>타입의
        타입의<TYPO descr="Typo: In word 'loadd'">loadd</TYPO>
        
        load이벤트가 발생하면 Event타입의 이벤트 객체가 생성된다.
        A뉴타운 112㎡형 분양가는 ㎡당 430만원, 강남 B아파트 ㎡당 1100만원 선 무너져. '사랑'은 85개 스크린에서 21만9천104명을 모아 '본 얼티메이텀'에 뒤졌으나 전국 400개 스크린에서는 86만7천287명을 동원, 서울보다는 지방 관객의 사랑을 더 많이 받은 것으로 나타났다.
        }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }
}