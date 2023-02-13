import java.io.File

class DecompilerConfig(configFilePath: String) {
    private val cfg = mutableMapOf<String, String>()

    init {
        val cfgInputStream = File(configFilePath).inputStream()
        cfgInputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val pair = line.split(" = ")
                cfg[pair[0]] = pair[1]
            }
        }
    }

    fun getConfig(name: String): String? {
        return cfg[name]
    }
}
