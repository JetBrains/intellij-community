interface MyInterface

interface GattClientScope {
    suspend fun await(block: () -> Unit)
    suspend fun readCharacteristic(p: MyInterface): Result<ByteArray>
    suspend fun writeCharacteristic(p: MyInterface, value: ByteArray): Result<Unit>
}
