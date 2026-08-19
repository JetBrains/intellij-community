package com.intellij.tools.ide.metrics.statistics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PerfolizerTest {

  private fun harrellDavisQuantileEstimatorCheck(values: List<Double>, ps: List<Double>, expected: List<Double>) {
    val n = ps.size
    for (i in 0 until n) {
      val actual = Perfolizer.HarrellDavisQuantileEstimator.quantile(Perfolizer.Sample(values), ps[i])
      assertEquals(expected[i], actual, 1e-5)
    }
  }

  private fun harrellDavisQuantileEstimatorCheck(values: List<Double>, weights: List<Double>, ps: List<Double>, expected: List<Double>) {
    val n = ps.size
    for (i in 0 until n) {
      val actual = Perfolizer.HarrellDavisQuantileEstimator.quantile(Perfolizer.Sample(values, weights), ps[i])
      assertEquals(expected[i], actual, 1e-5)
    }
  }

  private fun mannWhitneyCheck(x: List<Double>, y: List<Double>, expectedU: Double, expectedPValue: Double) {
    val result = Perfolizer.MannWhitneyTest.isGreater(Perfolizer.Sample(x), Perfolizer.Sample(y))
    println(result)
    assertEquals(expectedU, result.u, 1e-9)
    assertEquals(expectedPValue, result.pValue, 1e-9)
  }

  private fun mannWhitneyCheckInt(x: List<Int>, y: List<Int>, expectedU: Double, expectedPValue: Double) {
    mannWhitneyCheck(x.map { it.toDouble() }, y.map { it.toDouble() }, expectedU, expectedPValue)
  }

  private fun binomialCoefficientApproxCheck(n: Int, k: Int) {
    val actual = Perfolizer.MathHelper.binomialCoefficientApprox(n, k)
    val expected = Perfolizer.MathHelper.binomialCoefficient(n, k)
    assertEquals(expected.toDouble(), actual, 1e-5)
  }

  @Test
  fun harrellDavisQuantileEstimator01() {
    harrellDavisQuantileEstimatorCheck(
      listOf(0.0, 25.0, 50.0, 75.0, 100.0),
      listOf(0.0, 0.1, 0.2, 0.25, 0.3, 0.4, 0.5, 0.6, 0.7, 0.75, 0.8, 0.9, 1.0),
      listOf(0.0, 4.81290947065674, 13.7443607731199, 19.2481103578583, 25.1415863187833, 37.4702805366232, 50.0, 62.5297194633768,
             74.8584136812167, 80.7518896421417, 86.2556392268801, 95.1870905293433, 100.0)
    )
  }

  @Test
  fun harrellDavisQuantileEstimator02() {
    harrellDavisQuantileEstimatorCheck(
      listOf(1.0, 2.0, 3.0, 4.0, 5.0),
      listOf(1.0, 0.01, 0.0, 0.0, 1.0),
      listOf(0.5),
      listOf(2.99364)
    )
  }

  @Test
  fun harrellDavisQuantileEstimator03() {
    harrellDavisQuantileEstimatorCheck(
      listOf(1.0, 2.0, 3.0, 4.0, 5.0),
      listOf(1.0, 2.0, 3.0, 4.0, 5.0),
      listOf(0.1),
      listOf(1.6509670175492432)
    )
  }

  @Test
  fun regularizedIncompleteValue01() {
    assertEquals(0.838662379481348, Perfolizer.BetaFunction.regularizedIncompleteValue(0.6, 5.4, 0.2), 1e-5)
  }

  @Test
  fun mannWhitney01() = mannWhitneyCheckInt(listOf(1, 2, 3), listOf(4, 5, 6), 0.0, 1.0)

  @Test
  fun mannWhitney02() = mannWhitneyCheckInt(listOf(4, 5, 6), listOf(1, 2, 3), 9.0, 0.05)

  @Test
  fun mannWhitney03() = mannWhitneyCheckInt(listOf(2, 4, 6), listOf(1, 3, 5), 6.0, 0.35)

  @Test
  fun mannWhitney04() {
    val x = listOf(1650, 1088, 1568, 1050)
    val y = listOf(
      992, 1732, 922, 985, 985, 997, 814, 941, 941, 886, 965, 941, 900, 1003, 908, 1015, 903, 888, 931, 935, 890, 996, 885, 835, 1576,
      837, 894, 1064, 1054, 928, 946, 930, 896, 911, 964, 983, 1753, 1669, 871, 941, 984, 914, 934, 914, 965, 965, 992, 942, 902, 1012,
      949, 1052, 1083, 1065, 871, 928, 908, 933, 979, 970, 965, 927, 942, 898, 934, 990, 958, 1596, 1584, 922, 964, 972, 945, 961, 974,
      874, 849, 962, 885, 926, 911, 916, 977, 999, 845, 927, 913, 928, 1003, 857, 871, 927, 963, 955, 856, 919, 962, 863, 976, 964,
      954, 939, 878, 973, 950, 986, 899, 1015, 1128, 863, 969, 897, 912, 869, 928, 942, 938, 913, 927
    )
    mannWhitneyCheckInt(x, y, 448.0, 0.000237590914047604)
    mannWhitneyCheckInt(y, x, 28.0, 0.999789836131663)
  }

  @Test
  fun mannWhitney05() {
    val x= listOf(
      1.21531616164601, 1.18374783550839, 2.08604925494274, 1.02871298440532,
      2.04225410029393, 1.08826553194059, -0.408431890233717, 1.05522136279215,
      0.928694590200637, 1.3608671859535, 1.18839978525426, 0.443810735334692,
      1.08679615276034, 0.358970589606013, 1.36344973176639, 0.322534211411679,
      1.53281840429016, 0.673209402146606, 1.52096970640666, 0.686287759075184
    )
    val y = listOf(
      -0.475505176539298, -0.100043391690534, 1.9624124497391, 1.22760816029512,
      1.50416582087986, -2.24576163616019, -1.01486651091957, 0.991759200750696,
      -1.33658703533217, 0.215665357745819, -0.281310417973673, 1.16136180435176,
      -1.3188410614469, 0.124337393886294, 0.4808058811996, -0.94488723319193,
      -0.105023598537234, 0.696380543661041, -1.89174506379828, -0.914629400629872
    )
    mannWhitneyCheck(x, y, 319.0, 0.000466708806893553)
    mannWhitneyCheck(y, x, 81.0, 0.999582117108838)
  }

  @Test
  fun mannWhitney06() {
    val x= listOf(
      1.21531616164601, 1.18374783550839, 2.08604925494274, 1.02871298440532,
      2.04225410029393, 1.08826553194059, -0.408431890233717, 1.05522136279215,
      0.928694590200637, 1.3608671859535, 1.18839978525426, 0.443810735334692,
      1.08679615276034, 0.358970589606013, 1.36344973176639, 0.322534211411679,
      1.53281840429016, 0.673209402146606, 1.52096970640666, 0.686287759075184,
      0.524494823460703, 0.899956608309466, 2.9624124497391, 2.22760816029512,
      2.50416582087986, -1.24576163616019, -0.014866510919566, 1.9917592007507,
      -0.336587035332168, 1.21566535774582, 0.718689582026327, 2.16136180435176,
      -0.318841061446896, 1.12433739388629, 1.4808058811996, 0.0551127668080703,
      0.894976401462766, 1.69638054366104, -0.891745063798285, 0.0853705993701279
    )
    val y = listOf(
      0.0727821867078828, -0.361980824208187, 0.650073944801343,
      1.06569427454495, -0.00427698338455156, 0.970377011851032, -0.571546099711075,
      0.0177105330519745, 0.00616526336987138, 0.0939506021743026,
      0.0937300936674163, 0.610024434895758, -0.406556363944394, -0.575087039308514,
      1.24679525523924, 0.543930546775474, -0.474574466244513, 0.19374500075133,
      0.97979946959908, 2.40345784045136, -1.38458025369465, -1.12007514094462,
      -0.842949590024659, 0.942645577001221, -0.0384977130053118, -0.9929039576563,
      1.69859221803273, -2.11203196882785, 0.50220837873052, -1.55258990810461,
      0.171790824479609, 0.255720156327192, -0.256623176945548, -0.226050065633202,
      1.17697233898157, -0.435040816393167, 0.124153817642434, -0.298104693613031,
      -1.15894229012747, -1.26046030668232
    )
    mannWhitneyCheck(x, y, 1246.0, 4.69885196875944e-06)
    mannWhitneyCheck(y, x, 354.0, 0.999995531524269)
  }

  @Test
  fun mannWhitney07() {
    val x= listOf(
      1.21531616164601, 1.18374783550839, 2.08604925494274, 1.02871298440532,
      2.04225410029393, 1.08826553194059, -0.408431890233717, 1.05522136279215,
      0.928694590200637, 1.3608671859535
    )
    val y = listOf(
      0.188399785254256, -0.556189264665308, 0.0867961527603394,
      -0.641029410393987, 0.363449731766391
    )
    mannWhitneyCheck(x, y, 47.0, 0.00233100233100233)
    mannWhitneyCheck(y, x, 3.0, 0.998667998667999)
  }

  @Test
  fun mannWhitney08() {
    val x= listOf(
      1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
      18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33,
      34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49,
      50
    )
    val y = listOf(
      51, 52, 53, 54, 55
    )
    mannWhitneyCheckInt(x, y, 0.0, 1.0)
    mannWhitneyCheckInt(y, x, 250.0, 2.87458667036913e-07)
  }

  @Test
  fun mannWhitney09() {
    val x= listOf(
      1.21531616164601, 1.18374783550839, 2.08604925494274, 1.02871298440532,
      2.04225410029393, 1.08826553194059, -0.408431890233717, 1.05522136279215,
      0.928694590200637, 1.3608671859535, 1.18839978525426, 0.443810735334692,
      1.08679615276034, 0.358970589606013, 1.36344973176639, 0.322534211411679,
      1.53281840429016, 0.673209402146606, 1.52096970640666, 0.686287759075184,
      0.524494823460703, 0.899956608309466, 2.9624124497391, 2.22760816029512,
      2.50416582087986, -1.24576163616019, -0.014866510919566, 1.9917592007507,
      -0.336587035332168, 1.21566535774582, 0.718689582026327, 2.16136180435176,
      -0.318841061446896, 1.12433739388629, 1.4808058811996, 0.0551127668080703,
      0.894976401462766, 1.69638054366104, -0.891745063798285, 0.0853705993701279,
      1.07278218670788, 0.638019175791813, 1.65007394480134, 2.06569427454495,
      0.995723016615448, 1.97037701185103, 0.428453900288925, 1.01771053305197,
      1.00616526336987, 1.0939506021743, 1.09373009366742, 1.61002443489576,
      0.593443636055606, 0.424912960691486, 2.24679525523924, 1.54393054677547,
      0.525425533755487, 1.19374500075133, 1.97979946959908, 3.40345784045136,
      -0.384580253694647, -0.120075140944624, 0.157050409975341, 1.94264557700122,
      0.961502286994688, 0.00709604234370009, 2.69859221803273, -1.11203196882785,
      1.50220837873052, -0.552589908104612, 1.17179082447961, 1.25572015632719,
      0.743376823054452, 0.773949934366798, 2.17697233898157, 0.564959183606833,
      1.12415381764243, 0.701895306386969, -0.158942290127467, -0.26046030668232,
      2.26955445068243, 1.095882534509, 2.44140877041743, 1.28822151597719,
      0.345645134102906, 1.62045674459612, 1.12559586789111, 1.50405314409382,
      1.55741362739361, 0.101647061837636, 0.488781080235031, 1.12284983622172,
      1.30575045986636, 0.933924605810081, -0.130427893701558, 0.795807280873054,
      -0.642016104488539, 1.19949744870846, 0.804451620102355, 1.11542217964371
    )
    val y = listOf(
      -1.09327880966139
    )
    mannWhitneyCheck(x, y, 98.0, 0.0297029702970297)
    mannWhitneyCheck(y, x, 2.0, 0.98019801980198)
  }

  @Test
  fun mannWhitney10() {
    val x= (1..49).toList()
    val y = listOf(51)
    mannWhitneyCheckInt(y, x, 49.0, 0.02)
  }
  @Test
  fun mannWhitney11() {
    val x= (1..50).toList() + listOf(10_000)
    val y = listOf(51)
    mannWhitneyCheckInt(y, x, 50.0, 0.0384615384615385)
  }

  @Test
  fun binomialCoefficientApprox01() = binomialCoefficientApproxCheck(5, 2)

  @Test
  fun binomialCoefficientApprox02() = binomialCoefficientApproxCheck(10, 0)

  @Test
  fun binomialCoefficientApprox03() = binomialCoefficientApproxCheck(10, 5)

  @Test
  fun binomialCoefficientApprox04() = binomialCoefficientApproxCheck(10, 7)

  @Test
  fun binomialCoefficientApprox05() = binomialCoefficientApproxCheck(10, 10)

  @Test
  fun hyndmanFan01() {
    val x = listOf(17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17,
                   17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17,
                   20, 20, 18, 18, 18, 18, 18, 18, 18, 18, 18, 18, 18, 18, 18).map { it.toDouble() }
    val q = Perfolizer.HyndmanFanType7QuantileEstimator.quantile(Perfolizer.Sample(x), 0.8)
    assertEquals(17.2, q, 1e-9)
  }

  @Test
  fun cdf01() {
    val x = listOf(177, 175, 174, 176, 176, 175, 174, 174, 174, 175, 174, 174, 176, 175, 175, 173, 175, 175, 173, 175, 175, 174, 174, 174,
                   172, 172, 176, 174, 174, 175, 174, 175, 176, 175, 173, 175, 172, 175, 175, 175, 174, 175, 176, 175, 175, 174, 172, 175,
                   176, 177, 174, 176, 172, 175, 173, 175, 277, 267, 274, 275, 275, 274).map { it.toDouble() }
    val cdf = Perfolizer.CdfEstimator.cdfs(Perfolizer.Sample(x), listOf(177.0)).first()
    assertEquals(0.89, cdf, 1e-9)
  }

  @Test
  fun cdf02() {
    val x = listOf(1, 2, 3).map { it.toDouble() }
    val cdfs = Perfolizer.CdfEstimator.cdfs(Perfolizer.Sample(x), listOf(0.0, 1.0, 2.0, 3.0, 4.0))
    assertEquals(0.0, cdfs[0], 1e-9)
    assertEquals(0.0, cdfs[1], 1e-9)
    assertEquals(0.5, cdfs[2], 1e-9)
    assertEquals(1.0, cdfs[3], 1e-9)
    assertEquals(1.0, cdfs[4], 1e-9)
  }
  @Test
  fun roundDown() {
    val input = listOf(1, 12, 100, 105, 109, 190, 195, 199, 1000, 1099, 1900, 1999, 99_000_000_000, 99_999_999_999)
    val expected = listOf(1, 12, 100, 100, 100, 190, 190, 190, 1000, 1000, 1900, 1900, 99_000_000_000, 99_000_000_000)
    for (i in input.indices)
      assertEquals(expected[i], Perfolizer.Rounder.roundDown(input[i]))
  }

  @Test
  fun roundUp() {
    val input = listOf(1, 12, 100, 105, 109, 190, 195, 199, 1000, 1099, 1900, 1999, 99_000_000_000, 99_999_999_999)
    val expected = listOf(1, 12, 100, 110, 110, 190, 200, 200, 1000, 1100, 1900, 2000, 99_000_000_000, 100_000_000_000)
    for (i in input.indices) {
      val actual = Perfolizer.Rounder.roundUp(input[i])
      println("Input: ${input[i]} Actual: $actual Expected: ${expected[i]}")
      assertEquals(expected[i], actual)
    }
  }

  @Test
  fun shamos01() {
    val x = listOf(1, 2, 3, 4, 5).map { it.toDouble() }
    assertEquals(1.904071, Perfolizer.ShamosEstimator.scale(Perfolizer.Sample(x)), 1e-6)
  }

  @Test
  fun shamos02() {
    val x = listOf(
      1.078, 1.082, -1.104, -1.609, 1.812, -0.777, 0.574, -1.381, 1.713, -0.068, -1.326, 1.036, -1.007, -0.604, 1.121, 1.637, 0.589, -0.279,
      0.769, -1.457, -0.737, 0.607, 0.119, 0.566, 0.747, 0.69, 0.071, -0.116, -0.802, -0.702, 0.44, 0.525, 0.792, -0.331, 2.132, 0.811,
      1.419, -1.316, 0.087, 1.422, -1.042, -0.487, 0.09, 0.08, 0.473, 1.584, 0.995, 0.256, 0.416, 0.68)
    assertEquals(1.01251739974919, Perfolizer.ShamosEstimator.scale(Perfolizer.Sample(x)), 1e-6)
  }
}