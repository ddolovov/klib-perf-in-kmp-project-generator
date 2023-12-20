abstract class ZeroPadded<T : Any> {
    abstract val value: T
    abstract val paddedValue: String

    final override fun toString() = paddedValue
}

class ZeroPadding private constructor(private val paddingLength: Int) {
    fun pad(number: UInt): ZeroPadded<UInt> = ZeroPaddedUInt(number, number.toString().padStart(paddingLength, '0'))

    private data class ZeroPaddedUInt(override val value: UInt, override val paddedValue: String) : ZeroPadded<UInt>()

    companion object {
        fun createFor(maxNumber: UInt): ZeroPadding = ZeroPadding(maxNumber.toString().length)
    }
}

fun zeroPaddedSequence(maxNumber: UInt): Sequence<ZeroPadded<UInt>> {
    val padding = ZeroPadding.createFor(maxNumber)
    return generateSequence(1u) { current -> if (current != maxNumber) current + 1u else null }.map(padding::pad)
}
