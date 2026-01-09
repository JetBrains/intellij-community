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

  /** IntelliJ IDEA */
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

  /** PyCharm */
  val PY: IdeInfo = di.direct.instance<IdeProduct>().PY

  /** CLion */
  val CL: IdeInfo = di.direct.instance<IdeProduct>().CL

  /** DataSpell */
  val DS: IdeInfo = di.direct.instance<IdeProduct>().DS

  /** PyCharm Community */
  val PC: IdeInfo = di.direct.instance<IdeProduct>().PC

  /** Aqua */
  val QA: IdeInfo = di.direct.instance<IdeProduct>().QA

  /** RustRover */
  val RR: IdeInfo = di.direct.instance<IdeProduct>().RR

  /** Rider */
  val RD: IdeInfo = di.direct.instance<IdeProduct>().RD

  /* Gateway */
  val GW: IdeInfo = di.direct.instance<IdeProduct>().GW

  fun getProducts(): List<IdeInfo> = IdeProductProvider::class.declaredMemberProperties.map { it.get(IdeProductProvider) as IdeInfo }

  fun isProductSupported(productCode: String): Boolean = getProducts().any { it.productCode == productCode }
}