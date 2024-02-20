internal object J {
    @JvmStatic
    fun main(args: Array<String>) {
        println(0x1234 and (0x1234 ushr 1)) // 16
        println(1 or (2 shl 3)) // 17
        println(1 shl 2 or 3) // 7
        println(1 shl 2 or (3 ushr 4 and 5)) // 4
        println(1 or (2 shl 3 and (4 ushr 5))) // 1
        println(1 or (2 shl 3 and (4 ushr 5)) or 6 or (7 and 8)) // 7
        println(1 or (2 and (3 shl 4 ushr 5)) or 6 or (7 and 8)) // 7
        println(1 or (2 and ((3 shl 4) ushr 5)) or 6 or (7 and 8)) // 7
        println(1 or (2 shl 3)) // 17
        println(5 shl 16 or (0 shr 8) or 1) // 327681
        println(5 or (16 shl 0) or (8 shl 1)) // 21
        println(2 or (1 and 5)) // 3
        println((2 or 1) and 5) // 1
        println(false or (true and (5 == 5 ushr 7)))
        println(false or (true and (5 ushr 5 == 7)))
        println(false or ((5 ushr 5 == 7) and true))
        println(true and (4 >= 5 ushr 7) or false)
        println(true and (4 >= 5 == true) or false)
        println(true and (true == 5 >= 4) or false)

        val insideMultiline = (1
                + 1
                - 0x1234) and (0x1234 ushr 1)
        println(insideMultiline)

        val insideMultiline2 = (1
                + 1
                - 0x1234) and (0x1234 ushr 1)
        println(insideMultiline2)
    }
}
