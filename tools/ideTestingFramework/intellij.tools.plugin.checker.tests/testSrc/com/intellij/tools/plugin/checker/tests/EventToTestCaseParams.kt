package com.intellij.tools.plugin.checker.tests

import com.intellij.ide.starter.models.TestCase
import com.intellij.tools.plugin.checker.marketplace.MarketPlaceEvent

data class EventToTestCaseParams(val event: MarketPlaceEvent, val testCase: TestCase)
