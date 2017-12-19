/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.run

import com.intellij.openapi.util.JDOMExternalizerUtil
import com.jetbrains.reflection.Properties
import com.jetbrains.reflection.getProperties

/**
 * Tools that use [com.jetbrains.reflection.ReflectionUtilsKt] to serialize/deserialize configuration and copy its fields to form.
 *
 * Mark any primitive or String field ("var", not "val") with [ConfigField].
 * For structure-based fields use [com.jetbrains.reflection.DelegationProperty] and "val".
 * So, leaves are var, branches are val.
 *
 * You then create form with properties, use [ConfigurationWithFields] to copy properties to form and back,
 * and extension methods (for example [Properties.fillToXml]) to serialize/deserialize form
 * @see com.jetbrains.reflection.ReflectionUtilsKt
 */


@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
/**
 * Mark run configuration field with it to enable saving, restoring and form iteraction.
 * Make sure you use *var*, not *val*
 */
annotation class ConfigField


/**
 * Implement to get helper methods to copy to/from configuration form
 */
interface ConfigurationWithFields {
  fun copyFromForm(form: Any, javaBasedForm: Boolean = true) = copyFrom(getProperties(form, usePojoProperties = javaBasedForm))

  fun copyFrom(src: Properties) = src.copyTo(getConfigFields())

  fun copyTo(dst: Properties) = getConfigFields().copyTo(dst)

  fun copyToForm(form: Any, javaBasedForm: Boolean = true) = copyTo(getProperties(form, usePojoProperties = javaBasedForm))

  fun getConfigFields() = getConfigurationFields(this)
}

/**
 * @param configuration to get all its properties
 */
fun getConfigurationFields(configuration: Any) = getProperties(configuration, ConfigField::class.java)


/**
 * Serialize properties to [element], may prepend name with [prefix]]
 */
fun Properties.writeToXml(element: org.jdom.Element, prefix: String = "") {
  val gson = com.google.gson.Gson()
  properties.forEach {
    val value = it.get()
    if (value != null) {
      // No need to write null since null is default value
      JDOMExternalizerUtil.writeField(element, prefix + it.getName(), gson.toJson(value))
    }
  }
}

/**
 * Deserialize properties from [element], may prepend name with [prefix]]
 */
fun Properties.fillToXml(element: org.jdom.Element, prefix: String = "") {
  val gson = com.google.gson.Gson()
  this.properties.forEach {
    val fromJson: Any? = gson.fromJson(JDOMExternalizerUtil.readField(element, prefix + it.getName()), it.getType())
    if (fromJson != null) {
      it.set(fromJson)
    }
  }
}
