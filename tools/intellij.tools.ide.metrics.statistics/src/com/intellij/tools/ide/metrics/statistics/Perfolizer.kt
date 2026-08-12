package com.intellij.tools.ide.metrics.statistics

import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt


/**
 * Kotlin port of Andrey Akinshin's perfolizer statistics (https://github.com/AndreyAkinshin/perfolizer).
 *
 * The math is intentionally kept identical to Rider's Perforator copy
 * (`rider/test/framework-perforator/.../math/Perfolizer.kt`); do not change one without the other.
 */
@Suppress("SpellCheckingInspection")
object Perfolizer {
  object Rounder {
    fun roundDown(value: Long): Long {
      if (value < 100) return value

      val digits = floor(log10(value.toDouble())).toInt()
      val base = 10.0.pow(digits - 1).toLong()
      return (value / base) * base
    }

    fun roundUp(value: Long): Long {
      if (value <= 100) return value

      val digits = floor(log10(value.toDouble())).toInt()
      val base = 10.0.pow(digits - 1).toLong()
      return ((value + base - 1) / base) * base
    }

    fun roundDown(value: Double): Long = roundDown(value.toLong())
    fun roundUp(value: Double): Long = roundUp(value.toLong())
  }

  fun List<Double>.whichMax(start: Int, length: Int): Int {
    require(this.isNotEmpty()) { "List must not be empty" }
    require(start in indices) { "Start index must be in range [0, ${this.size - 1}]" }
    require(length in 1..(this.size - start)) { "Length must be in range [1, ${this.size - start}]" }

    var maxValue = this[start]
    var maxIndex = start
    for (i in start + 1 until start + length) {
      if (this[i] > maxValue) {
        maxValue = this[i]
        maxIndex = i
      }
    }
    return maxIndex
  }

  fun List<Double>.whichMin(start: Int, length: Int): Int {
    require(this.isNotEmpty()) { "List must not be empty" }
    require(start in indices) { "Start index must be in range [0, ${this.size - 1}]" }
    require(length in 1..(this.size - start)) { "Length must be in range [1, ${this.size - start}]" }

    var minValue = this[start]
    var minIndex = start
    for (i in start + 1 until start + length) {
      if (this[i] < minValue) {
        minValue = this[i]
        minIndex = i
      }
    }
    return minIndex
  }

  fun List<Double>.whichMax(): Int = this.whichMax(0, size)

  fun round(value: Double, precision: Int): Double {
    val factor = 10.0.pow(precision)
    return round(value * factor) / factor
  }

  class Sample(val values: List<Double>, weights: List<Double>) {
    constructor(values: List<Double>) : this(values, List(values.size) { 1.0 })

    val size: Int get() = values.size

    /**
     * Kish's effective sample size
     *
     * See:
     * * L. Kish “Survey sampling” (1965) // Publisher: John Wiley & Sons, Inc., New York, London. ISBN: 0-471-10949-5.
     * * Andrey Akinshin “Weighted quantile estimators” (2023) https://arxiv.org/abs/2304.07265
     */
    val weightedSize: Double = weights.sum().pow(2) / weights.sumOf { it * it }
    val indices: IntRange get() = values.indices

    val weights: List<Double>
    val sortedValues: List<Double>
    val sortedWeights: List<Double>
    val isWeighted: Boolean

    init {
      require(values.isNotEmpty()) { "Size of 'x' should be positive" }
      require(weights.isNotEmpty()) { "Size of 'w' should be positive" }
      require(values.size == weights.size) { "Sizes of 'x' and 'w' must be the same." }
      require(weights.sum() > 0) { "Sum of all weights should be positive" }
      require(weights.all { it >= 0 }) { "All weights should be non-negative" }

      val weightSum = weights.sum()
      this.weights = weights.map { it / weightSum }.toList()
      isWeighted = !this.weights.all { abs(it - 1.0 / weights.size) < 1e-9 }

      val pairs = values.zip(this.weights).sortedBy { it.first }
      sortedValues = pairs.map { it.first }.toList()
      sortedWeights = pairs.map { it.second }.toList()
    }

    override fun toString(): String {
      val parameters = if (abs(size - weightedSize) < 1e-9) "n=$size" else "n=$size;n*=${round(weightedSize, 3)}"
      return "Sample(${parameters})"
    }

    companion object {
      fun toSample(values: List<Double>): Sample? = if (values.isEmpty()) null else Sample(values)
    }
  }

  object WeightGenerator {
    // See: Andrey Akinshin “Weighted quantile estimators” (2023) https://arxiv.org/abs/2304.07265
    fun generateExponentialDecay(n: Int, halfLife: Int): List<Double> {
      val lambda = ln(2.0) / halfLife
      return (1..n).map { t -> exp(-lambda * (n - t)) }
    }

    fun generateEqual(n: Int): List<Double> {
      return (1..n).map { 1.0 / n }
    }
  }

  // Based on https://github.com/dotnet/BenchmarkDotNet/blob/a106b114b1f04fa1024be84a8969f5a168fa1c8b/src/BenchmarkDotNet/Mathematics/MathHelper.cs
  object MathHelper {
    fun gauss(x: Double): Double {
      val z: Double
      if (abs(x) < 1e-9) z = 0.0
      else {
        var y = abs(x) / 2
        if (y >= 3.0) z = 1.0
        else if (y < 1.0) {
          val w = y * y
          z = ((((((((0.000124818987 * w - 0.001075204047) * w + 0.005198775019) * w - 0.019198292004) * w + 0.059054035642) * w - 0.151968751364) * w + 0.319152932694) * w - 0.531923007300) * w + 0.797884560593) * y * 2.0
        }
        else {
          y -= 2.0
          z = (((((((((((((-0.000045255659 * y + 0.000152529290) * y - 0.000019538132) * y - 0.000676904986) * y + 0.001390604284) * y - 0.000794620820) * y - 0.002034254874) * y + 0.006549791214) * y - 0.010557625006) * y + 0.011630447319) * y - 0.009279453341) * y + 0.005353579108) * y - 0.002141268741) * y + 0.000535310849) * y + 0.999936657524
        }
      }

      return if (x > 0.0) (z + 1.0) / 2 else (1.0 - z) / 2
    }

    fun clamp(value: Int, min: Int, max: Int): Int = value.coerceAtLeast(min).coerceAtMost(max)
    fun clamp(value: Double, min: Int, max: Int): Double = value.coerceAtLeast(min.toDouble()).coerceAtMost(max.toDouble())

    fun binomialCoefficient(n: Int, k: Int): Long {
      val maxN = 65
      if (n !in 0..maxN) throw IllegalArgumentException("n=$n")
      if (k !in 0..n) return 0

      val pascalTriangle = Array(maxN + 1) { LongArray(maxN + 1) }
      for (i in 0..maxN) {
        pascalTriangle[i][0] = 1
        for (j in 1..i) pascalTriangle[i][j] = pascalTriangle[i - 1][j - 1] + pascalTriangle[i - 1][j]
      }
      return pascalTriangle[n][k]
    }

    fun binomialCoefficientApprox(n: Int, k: Int): Double {
      if (n <= 0) throw IllegalArgumentException("n")
      if (k !in 0..n) return 0.0

      fun logFactorial(m: Int) = GammaFunction.logValue((m + 1).toDouble())

      return exp(logFactorial(n) - logFactorial(k) - logFactorial(n - k))
    }
  }

  data class MannWhitneyTestResult(val n: Int, val m: Int, val u: Double, val pValue: Double)

  object MannWhitneyTest {
    private const val SMALL_N = 32

    // TODO: Migrate to the Löffler's implementation, https://aakinshin.net/posts/mw-loeffler/
    private fun pValueForSmallN(n: Int, m: Int, u: Int): Double {
      var q = u
      val nm = max(n, m)
      val w = Array(nm + 1) { Array(nm + 1) { LongArray(q + 1) } }
      for (i in 0..nm) for (j in 0..nm) for (k in 0..q) {
        if (i == 0 || j == 0 || k == 0) w[i][j][k] = (if (k == 0) 1 else 0).toLong()
        else if (k > i * j) w[i][j][k] = 0
        else if (i > j) w[i][j][k] = w[j][i][k]
        else if (k < j) w[i][j][k] = w[i][k][k]
        else w[i][j][k] = w[i - 1][j][k - j] + w[i][j - 1][k]
      }

      val denominator = MathHelper.binomialCoefficient(n + m, m)
      var p: Long = 0
      if (q <= n * m / 2) {
        for (i in 0..q) p += w[n][m][i]
      }
      else {
        q = n * m - q
        for (i in 0 until q) p += w[n][m][i]
        p = denominator - p
      }

      return p * 1.0 / denominator
    }

    // TODO: Migrate to the Löffler's implementation, https://aakinshin.net/posts/mw-loeffler/
    private fun pValueForBigN(n: Int, m: Int, u: Int): Double {
      var q = u
      val nm = max(n, m)
      val w = Array(nm + 1) { Array(nm + 1) { DoubleArray(q + 1) } }
      for (i in 0..nm) for (j in 0..nm) for (k in 0..q) {
        if (i == 0 || j == 0 || k == 0) w[i][j][k] = (if (k == 0) 1.0 else 0.0)
        else if (k > i * j) w[i][j][k] = 0.0
        else if (i > j) w[i][j][k] = w[j][i][k]
        else if (k < j) w[i][j][k] = w[i][k][k]
        else w[i][j][k] = w[i - 1][j][k - j] + w[i][j - 1][k]
      }

      val denominator = MathHelper.binomialCoefficientApprox(n + m, m)
      var p = 0.0
      if (q <= n * m / 2) {
        for (i in 0..q) p += w[n][m][i]
      }
      else {
        q = n * m - q
        for (i in 0 until q) p += w[n][m][i]
        p = denominator - p
      }

      return p * 1.0 / denominator
    }

    // returns p-value
    fun isGreater(x: Sample, y: Sample, thresholdValue: Double = 0.0): MannWhitneyTestResult {
      // TODO: upgrade implementation for double-sized samples
      val n = ceil(x.weightedSize).toInt()
      val m = ceil(y.weightedSize).toInt()

      var u = 0.0
      for (i in x.indices)
        for (j in y.indices)
          if (x.values[i] > y.values[j] + thresholdValue)
            u += x.weights[i] * y.weights[j]
      u = MathHelper.clamp(u, 0, 1) * n * m

      // All the approximations suck, we use only the exact algorithms
      // Edgeworth approximation is a backup plan if we increase the sample size, see https://aakinshin.net/posts/mw-edgeworth2/
      // Ties are ignored, see https://aakinshin.net/posts/mw-confusing-tie-correction/
      val pValue = if (n <= SMALL_N && m <= SMALL_N) {
        1 - pValueForSmallN(n, m, floor(u + 1e-9).toInt() - 1)
      }
      else {
        1 - pValueForBigN(n, m, floor(u + 1e-9).toInt() - 1)
      }

      return MannWhitneyTestResult(n, m, u, pValue)
    }
  }

  class StudentDistribution(val df: Double) {
    fun quantile(p: Double): Double {
      var x: Double = BetaFunction.regularizedIncompleteInverseValue(0.5 * df, 0.5, 2 * min(p, 1 - p))
      x = sqrt(df * (1 - x) / x)
      return if (p >= 0.5) x else -x
    }
  }

  data class ConfidenceInterval(val estimation: Double, val lower: Double, val upper: Double) {
    companion object {
      val NaN: ConfidenceInterval = ConfidenceInterval(Double.NaN, Double.NaN, Double.NaN)
    }
  }

  data class ConfidenceIntervalEstimator(val sampleSize: Double, val estimation: Double, val stdErr: Double) {
    private val degreeOfFreedom = sampleSize - 1

    private fun getZLevel(confidenceLevel: Double): Double {
      val x = 1 - (1 - confidenceLevel) / 2
      return StudentDistribution(degreeOfFreedom).quantile(x)
    }

    fun getCi(confidenceLevel: Double): ConfidenceInterval {
      if (degreeOfFreedom <= 0) return ConfidenceInterval(estimation, Double.NaN, Double.NaN)
      val margin = stdErr * getZLevel(confidenceLevel)
      return ConfidenceInterval(estimation, estimation - margin, estimation + margin)
    }
  }

  object GammaFunction {
    fun value(x: Double): Double {
      if (x < 1e-5) throw IllegalArgumentException("x should be positive (x = $x)")

      // For small x, the Stirling approximation has a noticeable error
      // We resolve this problem using Gamma(x) = Gamma(x+1)/x
      if (x < 1) return stirlingApproximation(x + 3) / x / (x + 1) / (x + 2)
      if (x < 2) return stirlingApproximation(x + 2) / x / (x + 1)
      if (x < 3) return stirlingApproximation(x + 1) / x

      return stirlingApproximation(x)

    }

    fun logValue(x: Double): Double {
      if (x < 1e-5) throw IllegalArgumentException("x should be positive (x = $x)")
      if (x < 3) return ln(value(x))
      return stirlingApproximationLog(x)
    }

    private fun stirlingApproximation(x: Double): Double {
      return sqrt(2 * PI / x) * (x / E).pow(x) * exp(getSeriesValue(x))
    }

    private fun stirlingApproximationLog(x: Double): Double {
      return x * ln(x) - x + ln(2 * PI / x) / 2 + getSeriesValue(x)
    }

    // sum = sum(b[2*n] / (2n * (2n-1) * z^(2n-1)))
    private fun getSeriesValue(x: Double): Double { // Bernoulli numbers
      val b2 = 1.0 / 6
      val b4 = -1.0 / 30
      val b6 = 1.0 / 42
      val b8 = -1.0 / 30
      val b10 = 5.0 / 66
      return b2 / 2 / x + b4 / 12 / x.pow(3) + b6 / 30 / x.pow(5) + b8 / 56 / x.pow(7) + b10 / 90 / x.pow(9)
    }
  }

  object BetaFunction {

    /**
     * Natural logarithm of Complete beta function B(a,b)
     */
    private fun completeLogValue(a: Double, b: Double): Double {
      return GammaFunction.logValue(a) + GammaFunction.logValue(b) - GammaFunction.logValue(a + b)
    }

    /**
     * Regularized incomplete beta function Ix(a, b)
     */
    fun regularizedIncompleteValue(a: Double,
                                   b: Double,
                                   x: Double): Double { // The implementation is inspired by "Incomplete Beta Function in C" (Lewis Van Winkle, 2017)
      // See https://codeplea.com/incomplete-beta-function-c for details
      //
      // We calculate the regularized incomplete beta function using a continued fraction (https://dlmf.nist.gov/8.17#v):
      //   Ix(a, b) = x^a * (1-x)^b / (a*B(a, b)) * 1 / (1 + d[1] / (1 + d[2] / (1 + d[3] / (...))))
      // where
      //   d[2m]   = m(b-m)x / (a+2m-1)(a+2m)
      //   d[2m+1] = -(a+m)(a+b+m)x / (a+2m)(a+2m+1)
      //
      // The approximated value of the continued fraction is calculated using the Lentz's algorithm
      if (a < 0) throw IllegalArgumentException("a should be non-negative (a = $a)")
      if (b < 0) throw IllegalArgumentException("a should be non-negative (a = $a)")

      val eps = 1e-8
      if (x < eps) return 0.0
      if (x > 1 - eps) return 1.0
      if (a < eps && b < eps) return 0.5
      if (a < eps) return 1.0
      if (b < eps) return 0.0

      // According to https://dlmf.nist.gov/8.17#v, the continued fraction converges rapidly for x<(a+1)/(a+b+2)
      // If x>=(a+1)/(a+b+2), we use Ix(a, b) = I{1-x}(b, a)
      if (x > (a + 1) / (a + b + 2)) return 1.0 - regularizedIncompleteValue(b, a, 1 - x)

      // We use the Lentz's algorithm to calculate the continued fraction
      //   f = 1 + d[1] / (1 + d[2] / (1 + d[3] / (...)))
      // The implementation is based on the following formulas:
      //   u[0] = 1, v[0] = 0, f[0] = 1
      //   u[i] = 1 + d[i] / u[i - 1]
      //   v[i] = 1 / (1 + d[i] * v[i - 1])
      //   f[i] = f[i - 1] * u[i] * v[i]
      val maxIterationCount = 300
      fun normalize(z: Double) = if (abs(z) < 1e-30) 1e-30 else z // Normalization prevents getting zero values
      var u = 1.0
      var v = 0.0
      var f = 1.0
      for (i in 0 until maxIterationCount) {
        var d: Double // d[i]
        val m = i / 2
        d = if (i == 0) 1.0 // d[0]
        else if (i % 2 == 0) m * (b - m) * x / ((a + 2 * m - 1) * (a + 2 * m)) // d[2m]
        else -((a + m) * (a + b + m) * x) / ((a + 2.0 * m) * (a + 2.0 * m + 1)) // d[2m+1]

        u = normalize(1 + d / u)
        v = 1 / normalize(1 + d * v)
        val uv = u * v
        f *= uv

        if (abs(uv - 1) < eps) break
      }

      // Ix(a, b) = x^a * (1-x)^b / (a*B(a, b)) * 1 / (1 + d[1] / (1 + d[2] / (1 + d[3] / (...))))
      return exp(ln(x) * a + ln(1.0 - x) * b - completeLogValue(a, b)) / a * (f - 1)
    }

    // The implementation is based on "Incomplete Beta Function" from "Numerical Recipes", 3rd edition, page 273
    fun regularizedIncompleteInverseValue(a: Double,
                                          b: Double,
                                          p: Double): Double {

      if (a < 0) throw IllegalArgumentException("a should be non-negative (a = $a)")
      if (b < 0) throw IllegalArgumentException("b should be non-negative (b = $b)")

      if (p <= 0) return 0.0
      if (p >= 1) return 1.0

      val eps = 1e-8
      var t: Double
      var u: Double
      var x: Double
      val w: Double

      if (a >= 1 && b >= 1) {
        val pp = if (p < 0.5) p else 1.0 - p
        t = sqrt(-2.0 * ln(pp))
        x = (2.30753 + t * 0.27061) / (1.0 + t * (0.99229 + t * 0.04481)) - t
        if (p < 0.5) x = -x
        val al = (x * x - 3.0) / 6.0
        val h = 2.0 / (1.0 / (2.0 * a - 1.0) + 1.0 / (2.0 * b - 1.0))
        w = x * sqrt(al + h) / h - (1.0 / (2.0 * b - 1) - 1.0 / (2.0 * a - 1.0)) * (al + 5.0 / 6.0 - 2.0 / (3.0 * h))
        x = a / (a + b * exp(2.0 * w))
      }
      else {
        val lna = ln(a / (a + b))
        val lnb = ln(b / (a + b))
        t = exp(a * lna) / a
        u = exp(b * lnb) / b
        w = t + u
        x = if (p < t / w) (a * w * p).pow(1.0 / a)
        else 1.0 - (b * w * (1.0 - p)).pow(1.0 / b)
      }

      val afac = -GammaFunction.logValue(a) - GammaFunction.logValue(b) + GammaFunction.logValue(a + b)
      for (iteration in 0 until 10) {
        if (x < eps || x > 1.0 - eps) return x // a or b are too small for accurate calculations

        val error = regularizedIncompleteValue(a, b, x) - p
        t = exp((a - 1) * ln(x) + (b - 1) * ln(1.0 - x) + afac)
        u = error / t
        t = u / (1.0 - 0.5 * min(1.0, u * ((a - 1) / x - (b - 1) / (1.0 - x)))) // Halley's method
        x -= t
        if (x <= 0.0) x = 0.5 * (x + t)
        if (x >= 1.0) x = 0.5 * (x + t + 1.0) // Bisect if x tries to go negative or > 1
        if (abs(t) < eps * x && iteration > 0) break
      }

      return x
    }
  }

  /**
   * Weighted Harrell-Davis quantile estimator
   *
   * See:
   * * Andrey Akinshin “Weighted quantile estimators” (2023) https://arxiv.org/abs/2304.07265
   * * Frank E Harrell, C E Davis “A new distribution-free quantile estimator” (1982) Biometrika. Vol. 69. No 3. Pp. 635–640. DOI: 10.1093/biomet/69.3.635
   */
  object HarrellDavisQuantileEstimator {
    fun quantiles(sample: Sample, p: List<Double>): List<Double> = p.map { this.quantile(sample, it) }.toList()

    fun quantile(sample: Sample, p: Double): Double {
      data class Item(val value: Double, val weight: Double)

      val values = sample.values
      val weights = sample.weights

      if (values.size != weights.size) throw Exception("values and weights have different sizes")
      if (values.isEmpty()) throw Exception("values is empty")
      val items = values.mapIndexed { index, value -> Item(value, weights[index]) }.sortedBy { it.value }
      val totalWeight = weights.sum()
      val n = sample.weightedSize
      val a = (n + 1) * p
      val b = (n + 1) * (1 - p)
      fun cdf(x: Double) = BetaFunction.regularizedIncompleteValue(a, b, x)

      var c1 = 0.0
      var betaCdfRight = 0.0
      var currentProbability = 0.0
      for (i in items.indices) {
        val betaCdfLeft = betaCdfRight
        currentProbability += items[i].weight / totalWeight
        val cdfValue = cdf(currentProbability)
        betaCdfRight = cdfValue
        val w = betaCdfRight - betaCdfLeft
        val value = items[i].value
        c1 += w * value
      }

      return c1
    }
  }

  /**
   * See: Rob J Hyndman, Yanan Fan “Sample Quantiles in Statistical Packages” (1996) The American Statistician. Vol. 50. No 4. Pp. 361. DOI: 10.2307/2684934
   */
  object HyndmanFanType7QuantileEstimator {
    fun quantile(sample: Sample, p: Double): Double {
      require(p in 0.0..1.0) { "'value' must be in the range [0, 1]" }

      val n = sample.size
      val h = MathHelper.clamp((n - 1) * p + 1, 1, n)
      val left = (h - 1) / n
      val right = h / n

      fun cdf(x: Double) = when {
        x <= left -> 0.0
        x >= right -> 1.0
        else -> x * n - h + 1
      }

      var result = 0.0
      var current = 0.0

      for (i in sample.indices) {
        val next = current + sample.sortedWeights[i]
        result += sample.sortedValues[i] * (cdf(next) - cdf(current))
        current = next
      }

      return result
    }

    fun quantiles(sample: Sample, ps: List<Double>): List<Double> = ps.map { p -> quantile(sample, p) }

    fun median(x: Sample): Double = quantile(x, 0.5)
  }

  object CdfEstimator {
    fun cdfs(sample: Sample, xs: List<Double>): List<Double> {
      val probabilities = (0..100).map { it / 100.0 }
      val percentiles = HyndmanFanType7QuantileEstimator.quantiles(sample, probabilities)
      return xs.map { x ->
        when (val index = percentiles.count { it < x }) {
          0 -> 0.0
          101 -> 1.0
          else -> if (percentiles[index] - percentiles[index - 1] > 1e-4)
            probabilities[index - 1] + 0.01 * (x - percentiles[index - 1]) / (percentiles[index] - percentiles[index - 1])
          else
            probabilities[index - 1]
        }
      }
    }
  }

  /**
   * Based on:
   * Hodges, J. L., and E. L. Lehmann. 1963. Estimates of location based on rank tests.
   * The Annals of Mathematical Statistics 34 (2):598–611.
   * DOI: 10.1214/aoms/1177704172
   */
  object HodgesLehmannEstimator {
    fun locationShift(x: Sample, y: Sample): Double {
      val diffs = mutableListOf<Double>()
      val diffsWeights = mutableListOf<Double>()
      for (i in x.indices)
        for (j in y.indices) {
          diffs.add(x.values[i] - y.values[j])
          diffsWeights.add(x.weights[i] * y.weights[j])
        }
      return HyndmanFanType7QuantileEstimator.median(Sample(diffs, diffsWeights))
    }

    /**
     * See: https://aakinshin.net/posts/hl-ratio/
     */
    fun locationRatio(x: Sample, y: Sample): Double {
      val diffs = mutableListOf<Double>()
      val diffsWeights = mutableListOf<Double>()
      for (i in x.indices)
        for (j in y.indices) {
          diffs.add(x.values[i] / y.values[j])
          diffsWeights.add(x.weights[i] * y.weights[j])
        }
      return HyndmanFanType7QuantileEstimator.median(Sample(diffs, diffsWeights))
    }
  }

  // Original work:
  // * Shamos, Michael Ian. “Geometry and Statistics: Problems at the Interface.” In Algorithms and Complexity. 1977.
  //
  // Comparison of the Shamos estimator to the Median Absolute Deviation and the Rousseeuw-Croux Qn scale estimators:
  // * https://aakinshin.net/posts/mad-vs-shamos/
  // * https://aakinshin.net/posts/shamos-vs-qn/
  object ShamosEstimator {
    private const val ASYMPTOTIC_BIAS = 0.9538726 // Φ(0.75) * sqrt(2)

    /**
     * The bias factor values were taken from Table A2 (Page 17) of the following paper:
     * * Park, Chanseok, Haewon Kim, and Min Wang.
     *   “Investigation of finite-sample properties of robust location and scale estimators.”
     *   Communications in Statistics-Simulation and Computation (2020): 1-27.
         https://doi.org/10.1080/03610918.2019.1699114
     */
    private val biasFactors = listOf(
      Double.NaN, Double.NaN,
      0.1831500, 0.2989400, 0.1582782, 0.1011748, 0.1005038, 0.0676993, 0.0609574, 0.0543760, 0.0476839, 0.0426722, 0.0385003, 0.0353028,
      0.0323526, 0.0299677, 0.0280421, 0.0262195, 0.0247674, 0.0232297, 0.0220155, 0.0208687, 0.0199446, 0.0189794, 0.0182343, 0.0174421,
      0.0166364, 0.0160158, 0.0153715, 0.0148940, 0.0144027, 0.0138855, 0.0134510, 0.0130228, 0.0127183, 0.0122444, 0.0118214, 0.0115469,
      0.0113206, 0.0109636, 0.0106308, 0.0104384, 0.0100693, 0.0098523, 0.0096735, 0.0094973, 0.0092210, 0.0089781, 0.0088083, 0.0086574,
      0.0084772, 0.0082120, 0.0081874, 0.0079775, 0.0078126, 0.0076743, 0.0075212, 0.0074051, 0.0072528, 0.0071807, 0.0070617, 0.0069123,
      0.0067833, 0.0066439, 0.0065821, 0.0064889, 0.0063844, 0.0062930, 0.0061910, 0.0061255, 0.0060681, 0.0058994, 0.0058235, 0.0057172,
      0.0056805, 0.0056343, 0.0055605, 0.0055011, 0.0053872, 0.0053062, 0.0052348, 0.0052075, 0.0051173, 0.0050697, 0.0049805, 0.0048705,
      0.0048695, 0.0048287, 0.0047315, 0.0046961, 0.0046698, 0.0046010, 0.0045544, 0.0045191, 0.0044245, 0.0044074, 0.0043579, 0.0043536,
      0.0042874, 0.0042520, 0.0041864)

    /**
     * Returns the scale factor to make the Shamos estimator consistent with the standard deviation under normality.
     */
    private fun factor(n: Int): Double = when {
      n <= 1 -> Double.NaN
      n <= 100 -> 1 / (ASYMPTOTIC_BIAS * (1 + biasFactors[n]))
      else -> 1 / (ASYMPTOTIC_BIAS * (1 + 0.414253297 / n - 0.442396799 / n / n))
    }

    fun scale(x: Sample): Double {
      val diffs = mutableListOf<Double>()
      val weights = mutableListOf<Double>()
      for (i in x.indices)
        for (j in x.indices)
          if (i < j) {
            diffs.add(abs(x.values[i] - x.values[j]))
            weights.add(x.weights[i] * x.weights[j])
          }
      return HyndmanFanType7QuantileEstimator.median(Sample(diffs, weights)) * factor(x.size)
    }
  }

  // https://aakinshin.net/posts/scale-measure-for-discrete-case/
  object DefenstiveScaleCorrector {
    fun scale(originalScale: Double, resolution: Double): Double = sqrt(originalScale.pow(2) + resolution.pow(2))
  }


  class DensityHistogramBin(val lower: Double, val upper: Double, val height: Double) {
    val middle: Double = (lower + upper) / 2
    override fun toString(): String = "[$lower .. $upper] / H=$height"
  }

  class DensityHistogram(val bins: List<DensityHistogramBin>) {
    val globalLower: Double = bins.first().lower
    val globalUpper: Double = bins.last().upper
  }

  /**
   * See: https://aakinshin.net/tags/qrde/
   */
  object QuantileRespectfulDensityHistogramBuilder {

    fun build(sample: Sample, binCount: Int): DensityHistogram {
      require(binCount > 1) { "Bin count should be more than 1" }

      val probabilities = (0..binCount).map { it * 1.0 / binCount }.toList()
      val quantiles = HarrellDavisQuantileEstimator.quantiles(sample, probabilities)

      val bins = mutableListOf<DensityHistogramBin>()
      for (i in 0 until binCount) {
        val width = quantiles[i + 1] - quantiles[i]
        if (width > 1e-9) {
          val value = 1.0 / binCount / width
          bins.add(DensityHistogramBin(quantiles[i], quantiles[i + 1], value))
        }
      }

      return DensityHistogram(bins)
    }
  }

  class RangedMode(val location: Double, val left: Double, val right: Double, val sample: Sample)
  class ModalityData(val modes: List<RangedMode>, val histogram: DensityHistogram) {
    val modality: Int = modes.size
  }

  /**
   * https://aakinshin.net/posts/lowland-multimodality-detection/
   */
  object LowlandModalityDetector {
    private const val SENSITIVITY = 0.5
    private const val PRECISION = 0.01

    // TODO: add jittering, see https://aakinshin.net/posts/discrete-sample-jittering2/
    fun detectModes(sample: Sample): ModalityData {
      if (sample.values.maxOrNull()!! - sample.values.minOrNull()!! < 1e-9)
        throw IllegalArgumentException("Sample should contain at least two different elements")

      val desiredBinCount = (1 / PRECISION).roundToInt()
      val histogram = QuantileRespectfulDensityHistogramBuilder.build(sample, desiredBinCount)
      val binCount = histogram.bins.count()
      val bins = histogram.bins
      val binArea = 1.0 / bins.count()
      val binHeights = bins.map { it.height }

      val peaks = mutableListOf<Int>()
      for (i in 1 until binCount - 1) {
        if (binHeights[i] > binHeights[i - 1] && binHeights[i] >= binHeights[i + 1]) {
          peaks.add(i)
        }
      }

      fun globalMode(location: Double) = RangedMode(location, histogram.globalLower, histogram.globalUpper, sample)

      fun localMode(location: Double, left: Double, right: Double): RangedMode {
        val modeValues = mutableListOf<Double>()
        val modeWeights = mutableListOf<Double>()
        for (i in sample.sortedValues.indices) {
          val value = sample.sortedValues[i]
          if (value in left..right) {
            modeValues.add(value)
            modeWeights.add(sample.sortedWeights[i])
          }
        }
        if (modeValues.isEmpty())
          throw IllegalStateException("Can't find any values in [$left, $right]")

        val modeSample = Sample(modeValues, modeWeights)
        return RangedMode(location, left, right, modeSample)
      }

      fun result(modes: List<RangedMode>): ModalityData = ModalityData(modes, histogram)

      when (peaks.size) {
        0 -> return result(listOf(globalMode(bins[binHeights.whichMax()].middle)))
        1 -> return result(listOf(globalMode(bins[peaks.first()].middle)))
        else -> {
          val modeLocations = mutableListOf<Double>()
          val cutPoints = mutableListOf<Double>()

          fun trySplit(peak0: Int, peak1: Int, peak2: Int): Boolean {
            var left = peak1
            var right = peak2
            val height = min(binHeights[peak1], binHeights[peak2])
            while (left < right && binHeights[left] > height)
              left++
            while (left < right && binHeights[right] > height)
              right--

            val width = bins[right].upper - bins[left].lower
            val totalArea = width * height
            val totalBinCount = right - left + 1
            val totalBinArea = totalBinCount * binArea
            val binProportion = totalBinArea / totalArea
            if (binProportion < SENSITIVITY) {
              modeLocations.add(bins[peak0].middle)
              cutPoints.add(bins[binHeights.whichMin(peak1, peak2 - peak1)].middle)

              return true
            }

            return false
          }

          val previousPeaks = mutableListOf(peaks[0])
          for (i in 1 until peaks.size) {
            val currentPeak = peaks[i]

            while (previousPeaks.isNotEmpty() && binHeights[previousPeaks.last()] < binHeights[currentPeak])
              if (trySplit(previousPeaks.first(), previousPeaks.last(), currentPeak))
                previousPeaks.clear()
              else
                previousPeaks.removeAt(previousPeaks.size - 1)

            if (previousPeaks.isNotEmpty() && binHeights[previousPeaks.last()] > binHeights[currentPeak])
              if (trySplit(previousPeaks.first(), previousPeaks.last(), currentPeak))
                previousPeaks.clear()

            previousPeaks.add(currentPeak)
          }

          modeLocations.add(bins[previousPeaks.first()].middle)

          val modes = mutableListOf<RangedMode>()
          when (modeLocations.size) {
            0 -> modes.add(globalMode(bins[binHeights.whichMax()].middle))
            1 -> modes.add(globalMode(modeLocations.first()))
            else -> {
              modes.add(localMode(modeLocations.first(), histogram.globalLower, cutPoints.first()))
              for (i in 1 until modeLocations.size - 1)
                modes.add(localMode(modeLocations[i], cutPoints[i - 1], cutPoints[i]))
              modes.add(localMode(modeLocations.last(), cutPoints.last(), histogram.globalUpper))
            }
          }

          return result(modes)
        }
      }
    }
  }

  data class PValueBunch(val pValue0: Double,
                         val pValue3: Double,
                         val pValue6: Double,
                         val pValue9: Double) {
    companion object {
      val NaN: PValueBunch = PValueBunch(Double.NaN, Double.NaN, Double.NaN, Double.NaN)

      fun createSingle(value: Double): PValueBunch = PValueBunch(value, Double.NaN, Double.NaN, Double.NaN)
    }

    fun isAnyLessThan(other: PValueBunch): Boolean =
      pValue0 < other.pValue0 ||
      pValue3 < other.pValue3 ||
      pValue6 < other.pValue6 ||
      pValue9 < other.pValue9

    fun isAllLessThan(other: PValueBunch): Boolean =
      (pValue0 < other.pValue0 || other.pValue0.isNaN()) &&
      (pValue3 < other.pValue3 || other.pValue3.isNaN()) &&
      (pValue6 < other.pValue6 || other.pValue6.isNaN()) &&
      (pValue9 < other.pValue9 || other.pValue9.isNaN())
  }

  data class Collation(
    // See https://aakinshin.net/posts/trinal-thresholds/
    val shift: Double,
    val ratio: Double,
    val effectSize: Double,
    val greater: PValueBunch,
    val lesser: PValueBunch) {

    fun isAbove(threshold: Collation): Boolean =
      shift.absoluteValue > threshold.shift &&
      ratio.absoluteValue > threshold.ratio &&
      effectSize.absoluteValue > threshold.effectSize &&
      (greater.isAllLessThan(threshold.greater) || lesser.isAllLessThan(threshold.lesser))

    companion object {
      val NaN: Collation = Collation(Double.NaN, Double.NaN, Double.NaN, PValueBunch.NaN, PValueBunch.NaN)
    }
  }

  object PerformanceChecker {
    private const val MIN_POSITIVE_ATTEMPTS = 3
    private const val MAX_POSITIVE_ATTEMPTS = 5

    const val DEFAULT_HISTORY_LIMIT: Int = 50

    fun check(history: List<Double>,
              current: List<Double>,
              isReliableCheck: Boolean = true,
              historyLimit: Int = DEFAULT_HISTORY_LIMIT): BenchmarkAnalysisResult {
      if (history.size <= 1 || current.isEmpty())
        return BenchmarkAnalysisResult(Sample.toSample(history), Sample.toSample(current), BenchmarkVerdict.STEADY,
                                        listOf("Empty history"), Collation.NaN, false)

      val muteReasons = mutableListOf<String>()

      return try {
        if (isReliableCheck) {
          val isReliable = getIsReliable(history, historyLimit)
          if (!isReliable)
            muteReasons.add("Metric is unreliable")
        }

        getPerformanceResult(history, current, muteReasons, historyLimit)
      }
      catch (e: Exception) {
        BenchmarkAnalysisResult(Sample.toSample(history), Sample.toSample(current), BenchmarkVerdict.STEADY, listOf("Exception: $e"),
                                 Collation.NaN, false)
      }
    }

    private fun getIsReliable(history: List<Double>, historyLimit: Int): Boolean {
      val checkMuteReasons = mutableListOf<String>()
      val minCheckHistorySize = max(historyLimit, history.size - MAX_POSITIVE_ATTEMPTS * 2)
      val maxCheckHistorySize = history.size - MIN_POSITIVE_ATTEMPTS
      if (minCheckHistorySize <= maxCheckHistorySize) {
        for (checkHistorySize in minCheckHistorySize..maxCheckHistorySize) {
          val checkHistory = history.take(checkHistorySize).takeLast(historyLimit)
          val minCheckCurrentSize = MIN_POSITIVE_ATTEMPTS
          val maxCheckCurrentSize = min(MAX_POSITIVE_ATTEMPTS, history.size - checkHistorySize)
          for (checkCurrentSize in minCheckCurrentSize..maxCheckCurrentSize) {
            val checkCurrent = history.drop(checkHistorySize).take(checkCurrentSize)
            val checkResult = getPerformanceResult(checkHistory, checkCurrent, checkMuteReasons, historyLimit)
            if (checkResult.verdict == BenchmarkVerdict.SLOWER_SEVERE) return false
          }
        }
      }
      return true
    }

    private fun collate(x: Sample, y: Sample): Collation {
      val shift = HodgesLehmannEstimator.locationShift(x, y)
      val ratio = HodgesLehmannEstimator.locationRatio(x, y)

      // Glass Delta style, see https://aakinshin.net/posts/gamma-es-cohen-glass/
      val scale = DefenstiveScaleCorrector.scale(ShamosEstimator.scale(y), 1.0)
      val effectSize = shift / scale

      fun multipleMannWhitney(a: Sample, b: Sample) = PValueBunch(
        MannWhitneyTest.isGreater(a, b).pValue,
        MannWhitneyTest.isGreater(a, b, 3 * scale).pValue,
        MannWhitneyTest.isGreater(a, b, 6 * scale).pValue,
        MannWhitneyTest.isGreater(a, b, 9 * scale).pValue
      )

      val greater = multipleMannWhitney(x, y)
      val lesser = multipleMannWhitney(y, x)

      return Collation(shift, ratio, effectSize, greater, lesser)
    }

    private fun getPerformanceResult(history: List<Double>,
                                     current: List<Double>,
                                     muteReasons: MutableList<String>,
                                     historyLimit: Int): BenchmarkAnalysisResult {
      val historyWeights = WeightGenerator.generateExponentialDecay(history.size, 50) // TODO: make parameter
      val currentWeights = WeightGenerator.generateEqual(current.size)
      var historySample = Sample(history, historyWeights)
      val currentSample = Sample(current, currentWeights)

      try {
        val modalityData = LowlandModalityDetector.detectModes(historySample)
        if (modalityData.modality > 1) {
          val relevantValues = mutableListOf<Double>()
          val relevantWeights = mutableListOf<Double>()
          var modeIndex = modalityData.modes.size - 1
          while (relevantValues.size < historyLimit && modeIndex >= 0) {
            relevantValues.addAll(modalityData.modes[modeIndex].sample.values)
            relevantWeights.addAll(modalityData.modes[modeIndex].sample.weights)
            modeIndex--
          }
          val relevantPairs = relevantValues.zip(relevantWeights).sortedBy { it.second }
          historySample = Sample(relevantPairs.map { it.first }, relevantPairs.map { it.second })
        }
      }
      catch (_: Exception) {
        // TODO
      }

      // TODO: add ED-PELT

      val change = collate(currentSample, historySample)
      val verdict = getVerdict(change, current.size)
      val canRetry = current.size < MAX_POSITIVE_ATTEMPTS

      return BenchmarkAnalysisResult(historySample, currentSample, verdict, muteReasons, change, canRetry)
    }

    // TODO: properly adjust the thresholds
    private fun getSuspicionThreshold(n: Int): Collation {
      val pn1 = PValueBunch(0.200, 0.100, Double.NaN, Double.NaN)
      val pn2 = PValueBunch(0.050, 0.050, Double.NaN, Double.NaN)
      val pn3 = PValueBunch(0.050, 0.050, Double.NaN, Double.NaN)
      val pn4 = PValueBunch(0.010, 0.010, Double.NaN, Double.NaN)
      val pn5 = PValueBunch(0.005, 0.005, Double.NaN, Double.NaN)
      val pBunch = when (n) {
        1 -> pn1
        2 -> pn2
        3 -> pn3
        4 -> pn4
        5 -> pn5
        else -> throw Exception("5 is the maximum number of iterations")
      }
      return Collation(100.0, 1.05, 3.0, pBunch, pBunch)
    }

    // TODO: properly adjust the thresholds
    private fun getModerateThreshold(n: Int): Collation? {
      val shift = 100.00 // Assuming unit is ms; TODO: support other measurement units
      val pn3 = PValueBunch(Double.NaN, 0.05, 0.05, 0.05) // ┌( ಠ‿ಠ )┘
      val pn4 = PValueBunch(Double.NaN, 0.01, 0.01, 0.01)
      val pn5 = PValueBunch(Double.NaN, 0.001, 0.001, 0.001)
      return when (n) {
        1, 2 -> null
        3 -> Collation(shift, 1.1, 10.0, pn3, pn3)
        4 -> Collation(shift, 1.05, 8.0, pn4, pn4)
        5 -> Collation(shift, 1.05, 6.0, pn5, pn5)
        else -> throw Exception("5 is the maximum number of iterations")
      }
    }

    private fun getSevereThreshold(n: Int): Collation? {
      val shift = 750.00 // Assuming unit is ms; TODO: support other measurement units
      val threshold = getModerateThreshold(n) ?: return null
      return Collation(shift, 1.15, threshold.effectSize, threshold.greater, threshold.lesser)
    }

    private fun getVerdict(change: Collation, iteration: Int): BenchmarkVerdict {
      val suspiciosThreshold = getSuspicionThreshold(iteration)
      val moderateThreshold = getModerateThreshold(iteration)
      val severeThreshold = getSevereThreshold(iteration)

      val severity = when {
        severeThreshold != null && change.isAbove(severeThreshold) -> BenchmarkSeverity.SEVERE
        moderateThreshold != null && change.isAbove(moderateThreshold) -> BenchmarkSeverity.MODERATE
        change.isAbove(suspiciosThreshold) -> BenchmarkSeverity.SUSPICIOUS
        else -> BenchmarkSeverity.STEADY
      }
      val direction = when {
        severity != BenchmarkSeverity.STEADY && change.effectSize > 0 -> BenchmarkDirection.SLOWER
        severity != BenchmarkSeverity.STEADY && change.effectSize < 0 -> BenchmarkDirection.FASTER
        else -> BenchmarkDirection.NEUTRAL
      }

      return BenchmarkVerdict(direction, severity)
    }
  }
}
