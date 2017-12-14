package com.intellij.stats.personalization

/**
 * @author Vitaliy.Bibaev
 */
abstract class UserFactorBase<in R : FactorReader>(override val id: String, private val descriptor: UserFactorDescription<*, R>) : UserFactor {
    override final fun compute(storage: UserFactorStorage): String? {
        return compute(storage.getFactorReader(descriptor))
    }

    abstract fun compute(reader: R): String?
}