package hr.fer.decotlin

class BasicBlock(
    val code: MutableMap<Int, String> = mutableMapOf(),
    val jumpTo: Int?
) {
    private var isHeader: Boolean = false

    fun containsByteIndex(byteIndex: Int): Boolean {
        return this.code.containsKey(byteIndex)
    }

    fun setBlockHeader() {
        this.isHeader = true
    }

    fun isHeader(): Boolean {
        return this.isHeader
    }

    override fun toString(): String {
        var result = ""
        code.forEach { entry ->
            if (entry.key >= 0) result += "${entry.key}"
            result += "\t${entry.value}\n"
        }
        return result
    }
}
