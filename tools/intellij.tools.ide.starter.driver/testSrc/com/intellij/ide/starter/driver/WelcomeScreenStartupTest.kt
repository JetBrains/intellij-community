package com.intellij.ide.starter.driver

import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.NonModalWelcomeScreenUI
import com.intellij.driver.sdk.ui.components.common.welcomeScreen
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.junit5.newContext
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.runner.Starter
import com.intellij.tools.ide.starter.product.idea.ultimate.IdeaUltimate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class WelcomeScreenStartupTest {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun selectsWelcomeScreenAtStartup(nonModal: Boolean) {
    Starter.newContext(IdeInfo.IdeaUltimate, "WelcomeScreenStartup-$nonModal") {
      systemProperties = mapOf("idea.welcome.screen.non.modal.enabled" to nonModal.toString())
    }.runIdeWithDriver().useDriverAndCloseIde {
      step("Find the welcome screen at startup") {
        welcomeScreen {
          assertEquals(nonModal, this is NonModalWelcomeScreenUI)
          openProjectButton.waitFound()
        }
      }
    }
  }
}
