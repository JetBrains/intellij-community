package com.intellij.ide.starter.data

import com.intellij.ide.starter.models.IdeProduct
import com.intellij.ide.starter.models.StartUpPerformanceCase

abstract class TestCaseTemplate(val ideProduct: IdeProduct) {
  protected fun getTemplate() = StartUpPerformanceCase(
    ideInfo = ideProduct.ideInfo
  )
}

