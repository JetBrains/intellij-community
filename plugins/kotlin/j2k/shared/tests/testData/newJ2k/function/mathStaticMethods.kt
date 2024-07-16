import kotlin.math.IEEErem
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.expm1
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.nextDown
import kotlin.math.nextTowards
import kotlin.math.nextUp
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh
import kotlin.math.withSign

class J {
    fun foo(x: Double, y: Double, f: Float) {
        abs(x)
        acos(x)
        asin(x)
        atan(x)
        atan2(x, y)
        cbrt(x)
        ceil(x)
        x.withSign(y)
        cos(x)
        cosh(x)
        exp(x)
        expm1(x)
        floor(x)
        hypot(x, y)
        x.IEEErem(y)
        ln(x)
        ln1p(x)
        log10(x)
        max(x, y)
        min(x, y)
        x.nextTowards(y)
        x.nextDown()
        x.nextUp()
        x.pow(y)
        round(x)
        // Don't convert `Math.round` calls, because Kotlin's `round` throws an exception for a NaN argument
        Math.round(x)
        Math.round(f)
        sign(x)
        sin(x)
        sinh(x)
        sqrt(x)
        tan(x)
        tanh(x)
    }
}
