// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promotion

import com.intellij.icons.AllIcons
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.options.ConfigurableWithId
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FeaturePromoBundle
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import javax.swing.Icon
import javax.swing.JComponent
import kotlin.reflect.KClass

internal abstract class ProPromoConfigurable : ConfigurableWithId, Configurable.Promo {
  override fun isModified(): Boolean = false
  override fun apply() = Unit
  override fun getPromoIcon(): Icon = AllIcons.Ultimate.Lock
}

internal abstract class ProPromoConfigurableProvider(private val clazz: KClass<out Configurable>) : ConfigurableProvider() {
  final override fun createConfigurable(): Configurable? {
    return clazz.java.getConstructor().newInstance()
  }
}

internal class PromoDatabaseConfigurableProvider : ProPromoConfigurableProvider(PromoDatabaseConfigurable::class)
internal class PromoJSConfigurableProvider : ProPromoConfigurableProvider(PromoJSConfigurable::class)
internal class PromoTSConfigurableProvider : ProPromoConfigurableProvider(PromoTSConfigurable::class)
internal class PromoDjangoConfigurableProvider : ProPromoConfigurableProvider(PromoDjangoConfigurable::class)
internal class PromoJupyterConfigurableProvider : ProPromoConfigurableProvider(PromoJupyterConfigurable::class)
internal class PromoRemoteSshConfigurableProvider : ProPromoConfigurableProvider(PromoRemoteSshConfigurable::class)


internal class PromoDatabaseConfigurable : ProPromoConfigurable() {
  override fun getId(): String = "promo.database"
  override fun getDisplayName(): String = FeaturePromoBundle.message("promo.configurable.database")

  override fun createComponent(): JComponent {
    return databaseFeatures(PromoEventSource.SETTINGS)
  }
}

internal class PromoJSConfigurable : ProPromoConfigurable() {
  override fun getId(): String = "promo.javascript"
  override fun getDisplayName(): String = FeaturePromoBundle.message("promo.configurable.javascript")
  override fun createComponent(): JComponent = javascriptFeatures(PromoEventSource.SETTINGS, PromoTopic.JavaScript)
}

internal class PromoTSConfigurable : ProPromoConfigurable() {
  override fun getId(): String = "promo.typescript"
  override fun getDisplayName(): String = FeaturePromoBundle.message("promo.configurable.typescript")
  override fun createComponent(): JComponent = javascriptFeatures(PromoEventSource.SETTINGS, PromoTopic.TypeScript)
}

internal class PromoDjangoConfigurable : ProPromoConfigurable() {
  override fun getId(): String = "promo.django"
  override fun getDisplayName(): String = PyCharmCommunityCustomizationBundle.message("promo.configurable.django")
  override fun createComponent(): JComponent =  djangoFeatures(PromoEventSource.SETTINGS)
}

internal class PromoJupyterConfigurable : ProPromoConfigurable() {
  override fun getId(): String = "promo.jupyter"
  override fun getDisplayName(): String = PyCharmCommunityCustomizationBundle.message("promo.configurable.jupyter")
  override fun createComponent(): JComponent =  jupyterFeatures(PromoEventSource.SETTINGS)
}

internal class PromoRemoteSshConfigurable : ProPromoConfigurable() {
  override fun getId(): String = "promo.remoteSsh"
  override fun getDisplayName(): String = PyCharmCommunityCustomizationBundle.message("promo.configurable.remoteSsh")
  override fun createComponent(): JComponent = sshFeatures(PromoEventSource.SETTINGS)
}