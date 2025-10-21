package com.jetbrains.python.ast.impl

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ValueHolder<T>(@JvmField val value: T)