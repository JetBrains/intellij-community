// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn

import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.net.HttpProxyConfigurable
import org.jetbrains.idea.svn.config.SvnConfigureProxiesDialog

internal class NetworkSvnConfigurable(private val project: Project) : BoundSearchableConfigurable(
  SvnBundle.message("configurable.name.svn.network"),
  SvnConfigurable.HELP_ID + ".Network",
  SvnConfigurable.ID + ".Network"
), NoScroll {

  private lateinit var httpTimeout: JBIntSpinner

  override fun createPanel(): DialogPanel {
    lateinit var result: DialogPanel
    val settings = SvnConfiguration.getInstance(project)

    result = panel {
      row {
        checkBox(SvnBundle.message("use.idea.proxy.as.default", ApplicationNamesInfo.getInstance().productName))
          .comment(SvnBundle.message("use.idea.proxy.as.default.label.text"))
          .bindSelected(settings::isUseDefaultProxy, settings::setUseDefaultProxy)

        link(SvnBundle.message("navigate.to.idea.proxy.settings")) {
          val settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(result))
          settings?.select(settings.find(HttpProxyConfigurable::class.java))
        }.align(AlignX.RIGHT)
      }

      row(SvnBundle.message("settings.HTTP.timeout")) {
        httpTimeout = secondsSpinner(settings::getHttpTimeout, settings::setHttpTimeout)
          .component
      }
      row(SvnBundle.message("settings.SSH.connection.timeout")) {
        secondsSpinner(settings::getSshConnectionTimeout, settings::setSshConnectionTimeout)
      }
      row(SvnBundle.message("settings.SSH.read.timeout")) {
        secondsSpinner(settings::getSshReadTimeout, settings::setSshReadTimeout)
      }
      buttonsGroup {
        row(SvnBundle.message("settings.ssl.protocols")) {
          radioButton(SvnBundle.message("settings.ssl.protocols.all"), SvnConfiguration.SSLProtocols.all)
          radioButton(SvnBundle.message("settings.ssl.protocols.sslv3"), SvnConfiguration.SSLProtocols.sslv3)
          radioButton(SvnBundle.message("settings.ssl.protocols.all.tlsv1"), SvnConfiguration.SSLProtocols.tlsv1)
        }
      }.bind(settings::getSslProtocols, settings::setSslProtocols)

      row {
        label("")
      }.resizableRow()

      row {
        button(SvnBundle.message("button.text.edit.proxies")) {
          val dialog = SvnConfigureProxiesDialog(project)
          dialog.show()
          httpTimeout.value = SvnConfiguration.getInstance(project).getHttpTimeout() / 1000
        }.commentRight(SvnBundle.message("settings.edit.servers.subversion.runtime.configuration.file"))
      }
    }

    return result
  }

  private fun Row.secondsSpinner(millisGetter: () -> Long, millisSetter: (Long) -> Unit): Cell<JBIntSpinner> {
    val result = spinner(0..30 * 60, 10)
      .gap(RightGap.SMALL)
      .bindIntValue(getter = { (millisGetter() / 1000).toInt() }, setter = { millisSetter(it * 1000L) })

    @Suppress("DialogTitleCapitalization")
    label(SvnBundle.message("settings.seconds"))

    return result
  }
}
