// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.extensions

import com.intellij.navigation.NavigationItem
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.nameResolver.FQNamesProvider
import com.jetbrains.python.psi.PyQualifiedNameOwner

fun FQNamesProvider.getQualifiedNames() = names.map(QualifiedName::fromDottedString).toTypedArray()

fun FQNamesProvider.getFirstName() = names[0]!!

fun FQNamesProvider.shortNameMatches(item: NavigationItem) = item.name.run { this in getShortNames() }

/**
 * @return all names in unqualified ("after last dot") format
 */
fun FQNamesProvider.getShortNames() = getQualifiedNames().mapNotNull(QualifiedName::getLastComponent).toList()

/**
 * Checks if element name matches. [.alwaysCheckQualifiedName] controls if full name should be checked, or only last and first
 * parts (name and package) are enough. It may be used for cases when physical FQN is not documented.
 */
fun FQNamesProvider.isNameMatches(qualifiedNameOwner: PyQualifiedNameOwner): Boolean {
  val qualifiedName = qualifiedNameOwner.qualifiedName ?: return false

  // Only check qualified name
  if (alwaysCheckQualifiedName()) {
    return qualifiedName in names
  }

  // Relaxed check: package and name
  val elementQualifiedName = QualifiedName.fromDottedString(qualifiedName)
  return getQualifiedNames().any {
    return it.firstComponent == elementQualifiedName.firstComponent &&
           it.lastComponent == elementQualifiedName.lastComponent
  }
}