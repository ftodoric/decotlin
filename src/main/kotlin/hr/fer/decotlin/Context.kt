package hr.fer.decotlin

import DecompilerConfig
import org.apache.bcel.classfile.JavaClass
import java.io.File

class Context(private val config: DecompilerConfig) {
    private lateinit var classFile: JavaClass
    lateinit var targetFile: File
    var targetCFGFile: File = File(config.getConfig("decompiledCFGPath")!!)

    // Every symbol is unique, prefixed by its block key
    private var symbolTable: MutableMap<String, String> = mutableMapOf()

    // Viable definitions are mapped to each and every block
    // Key is block, value is a symbol name that can be referenced in the symbol table
    private var viableOutReachDefs: MutableMap<Int, List<String>> = mutableMapOf()
    private var viableInReachDefs: MutableMap<Int, List<String>> = mutableMapOf()

    fun setClassFile(classFile: JavaClass) {
        this.classFile = classFile
        setTargetFile()
    }

    fun getClassFile(): JavaClass {
        return this.classFile
    }

    private fun setTargetFile() {
        var fileName = classFile.className.split(".").last()
        fileName = fileName.substring(0, fileName.length - 2) + ".kt"
        targetFile = File(config.getConfig("decompiledSourcesPath") + fileName)
    }

    fun setSymbol(symbolName: String, value: String) {
        this.symbolTable[symbolName] = value
    }

    fun getSymbol(symbolName: String): String? {
        return this.symbolTable[symbolName]
    }

    fun setOutReachDef(blockKey: Int, defName: String) {
        this.viableOutReachDefs[blockKey] = listOf(defName)
    }

    fun getOutReachDefs(blockKey: Int): List<String>? {
        return this.viableOutReachDefs[blockKey]
    }

    fun setInReachDef(blockKey: Int, defName: String) {
        this.viableInReachDefs[blockKey] = listOf(defName)
    }

    fun getInReachDefs(blockKey: Int): List<String>? {
        return this.viableInReachDefs[blockKey]
    }

    fun getBlockForSymbolName(targetSymbolName: String): String? {
        for (symbolName in this.symbolTable.values) {
            if (symbolName.matches("""${targetSymbolName}\^""".toRegex())) return symbolName.replace(
                """${targetSymbolName}\^""".toRegex(),
                ""
            )
        }
        return null
    }
}
