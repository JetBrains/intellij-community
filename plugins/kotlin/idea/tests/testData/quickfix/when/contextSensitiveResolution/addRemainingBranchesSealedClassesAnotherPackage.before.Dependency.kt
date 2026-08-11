package pkg

sealed class MyResult {
    class Ok(val v: String) : MyResult()
    class Err(val m: String) : MyResult()
}
