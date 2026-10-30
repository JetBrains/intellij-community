// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeItemDataFactory
import com.intellij.platform.searchEverywhere.SeSessionImpl
import com.intellij.platform.searchEverywhere.impl.SeItemRegistry
import com.intellij.platform.searchEverywhere.presentations.SeBasicItemPresentationBuilder
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.toProviderId
import java.util.UUID

/**
 * Creates [SeItemData] in a test, as a Search Everywhere provider does.
 *
 * The items stay in [SeItemRegistry] until [close], so [SeItemData.fetchItemIfExists] finds them until then.
 */
internal class TestItemDataFactory : AutoCloseable {
  private val session = SeSessionImpl(UUID.randomUUID().toString())

  /** Returns the item data of an item with [rawObject] that the provider with [providerId] found. */
  suspend fun create(rawObject: Any, providerId: String, weight: Int = 0, uuid: String = UUID.randomUUID().toString()): SeItemData {
    val item = TestItem(rawObject, weight)
    return checkNotNull(SeItemDataFactory().createItemData(session, uuid, item, providerId.toProviderId(), emptyMap())) {
      "The Search Everywhere session of the test is closed"
    }
  }

  override fun close() {
    SeItemRegistry.getInstance().closeSession(session)
  }
}

private class TestItem(override val rawObject: Any, private val weight: Int) : SeItem {
  override fun weight(): Int = weight

  override suspend fun presentation(): SeItemPresentation = SeBasicItemPresentationBuilder().withText(rawObject.toString()).build()
}
