package com.intellij.ide.starter.data

import com.intellij.ide.starter.models.IdeProduct
import com.intellij.ide.starter.models.TestCase

abstract class TestCaseTemplate(val ideProduct: IdeProduct) {
  protected fun getTemplate() = TestCase(
    ideInfo = ideProduct.ideInfo
  )
}

