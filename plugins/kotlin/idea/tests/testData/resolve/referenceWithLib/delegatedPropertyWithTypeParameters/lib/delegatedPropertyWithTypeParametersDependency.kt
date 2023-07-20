package dependency

import kotlin.reflect.KProperty

public operator fun <T, R> T.getValue(thisRef: R, desc: KProperty<*>): Int {
    return 3
}

public operator fun <T, R> T.setValue(thisRef: R, desc: KProperty<*>, value: Int) {
}
