package zero

import one.delegate.MovingDelegate
import one.delegate.MovingDelegateExtended
import one.delegate.getValue

val delegatedVal1: Int by MovingDelegate()

val delegatedVal2: Int by MovingDelegateExtended()