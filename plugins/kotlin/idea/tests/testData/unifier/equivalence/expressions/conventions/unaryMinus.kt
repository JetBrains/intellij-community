fun foo() {
    <selection>-1</selection>
    1.unaryMinus()
}

// test data for K1 and K2 is different because `-` in `-1` is not resolved to call `unaryMinus()` in K2, and fir for `-1` is a constant expression