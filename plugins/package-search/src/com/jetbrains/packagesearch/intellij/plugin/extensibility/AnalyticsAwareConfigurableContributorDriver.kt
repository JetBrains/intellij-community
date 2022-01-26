package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.internal.statistic.eventLog.events.EventPair

/**
 * A [ConfigurableContributorDriver] that is also able to provide analytics data for events.
 * Only for internal usage, due to FUS policy of explicitly allow-listing logged data.
 */
internal interface AnalyticsAwareConfigurableContributorDriver : ConfigurableContributorDriver {

    fun provideApplyEventAnalyticsData(): List<EventPair<*>>
}
