package com.intellij.psi.xml

import com.intellij.lang.Language
import com.intellij.lang.html.HTMLLanguage
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import kotlin.reflect.KClass
import kotlin.reflect.cast

internal class BasicXmlElementTypeFactory {
  fun getFileElementType(
    name: String,
  ): IFileElementType {
    return getField(name, IFileElementType::class)
           ?: IFileElementType(name, HTMLLanguage.INSTANCE)
  }

  fun getElementType(
    name: String,
    language: Language = HTMLLanguage.INSTANCE,
  ): IElementType {
    return getField(name, IElementType::class)
           ?: IElementType(name, language)
  }

  private fun <T : IElementType> getField(
    name: String,
    clazz: KClass<T>,
  ): T? {
    val holder = try {
      Class.forName("com.intellij.psi.xml.XmlElementTypeImpl")
    }
    catch (e: ClassNotFoundException) {
      return null
    }

    return holder
      .getDeclaredField(name)
      .get(null)
      .let(clazz::cast)
  }
}
