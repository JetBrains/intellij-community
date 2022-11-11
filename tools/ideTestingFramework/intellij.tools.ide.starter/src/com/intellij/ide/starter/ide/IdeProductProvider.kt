package com.intellij.ide.starter.ide

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeProduct
import org.kodein.di.direct
import org.kodein.di.instance
import kotlin.reflect.full.declaredMemberProperties

object IdeProductProvider {
  /** GoLand */
  val GO: IdeInfo = di.direct.instance<IdeProduct>().GO

  /** IntelliJ Ultimate */
  val IU: IdeInfo = di.direct.instance<IdeProduct>().IU

  /** IntelliJ Community */
  val IC: IdeInfo = di.direct.instance<IdeProduct>().IC

  /** Android Studio */
  val AI: IdeInfo = di.direct.instance<IdeProduct>().AI

  /** WebStorm */
  val WS: IdeInfo = di.direct.instance<IdeProduct>().WS

  /** PhpStorm */
  val PS: IdeInfo = di.direct.instance<IdeProduct>().PS

  /** DataGrip */
  val DB: IdeInfo = di.direct.instance<IdeProduct>().DB

  /** RubyMine */
  val RM: IdeInfo = di.direct.instance<IdeProduct>().RM

  /** PyCharm Professional */
  val PY: IdeInfo = di.direct.instance<IdeProduct>().PY

  /** CLion */
  val CL: IdeInfo = di.direct.instance<IdeProduct>().CL

  fun getProducts(): List<IdeInfo> = IdeProductProvider::class.declaredMemberProperties.map { it.get(IdeProductProvider) as IdeInfo }
}