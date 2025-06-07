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

        // Don't convert `Math.nextAfter`, because Kotlin's `nextTowards` overloads have slightly different signatures
        Math.nextAfter(x, y)

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

    fun overloads(s: Short, b: Byte, i: Int, l: Long, f: Float, d: Double) {
        abs(s.toInt())
        abs(b.toInt())
        abs(i)
        abs(l)
        abs(f)
        abs(d)
        abs(1)
        abs(1.0)

        max(s.toInt(), i)
        max(b.toInt(), i)
        max(i, i)
        max(i.toLong(), l)
        max(i.toFloat(), f)
        max(i.toDouble(), d)
        max(1, 2)
        max(1.0, 2.0)

        min(s.toInt(), i)
        min(b.toInt(), i)
        min(i, i)
        min(i.toLong(), l)
        min(i.toFloat(), f)
        min(i.toDouble(), d)
        min(1, 2)
        min(1.0, 2.0)
    }

    fun floatingPointOverloads(i: Int, f: Float, d: Double) {
        i.toFloat().withSign(f)
        f.withSign(f)
        i.toDouble().withSign(d)
        d.withSign(d)
        1.0f.withSign(f)
        1.0.withSign(2.0)

        // Don't convert `Math.nextAfter`, because Kotlin's `nextTowards` overloads have slightly different signatures
        Math.nextAfter(i.toFloat(), f.toDouble())
        Math.nextAfter(f, f.toDouble())
        Math.nextAfter(i.toFloat(), d)
        Math.nextAfter(d, d)
        Math.nextAfter(1.0f, f.toDouble())
        Math.nextAfter(1.0, 2.0)

        i.toFloat().nextDown()
        f.nextDown()
        d.nextDown()
        1f.nextDown()
        1.0f.nextDown()
        1.0.nextDown()

        i.toFloat().nextUp()
        f.nextUp()
        d.nextUp()
        1f.nextUp()
        1.0f.nextUp()
        1.0.nextUp()

        sign(i.toFloat())
        sign(f)
        sign(d)
        sign(1f)
        sign(1.0f)
        sign(1.0)
    }
}
