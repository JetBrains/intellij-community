package com.jetbrains.packagesearch.intellij.plugin.ui.util

import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager2

internal abstract class AbstractLayoutManager2 : LayoutManager2 {

    override fun addLayoutComponent(comp: Component, constraints: Any?) {
        throw UnsupportedOperationException()
    }

    override fun addLayoutComponent(name: String, comp: Component) {
        throw UnsupportedOperationException()
    }

    override fun removeLayoutComponent(comp: Component) {
        throw UnsupportedOperationException()
    }

    override fun preferredLayoutSize(parent: Container): Dimension? {
        throw UnsupportedOperationException()
    }

    override fun minimumLayoutSize(parent: Container): Dimension? {
        throw UnsupportedOperationException()
    }

    override fun layoutContainer(parent: Container) {
        throw UnsupportedOperationException()
    }

    override fun maximumLayoutSize(target: Container): Dimension? {
        throw UnsupportedOperationException()
    }

    override fun getLayoutAlignmentX(target: Container): Float {
        throw UnsupportedOperationException()
    }

    override fun getLayoutAlignmentY(target: Container): Float {
        throw UnsupportedOperationException()
    }

    override fun invalidateLayout(target: Container) {
        throw UnsupportedOperationException()
    }
}
