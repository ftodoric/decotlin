package hr.fer.decotlin.m03

import hr.fer.decotlin.BasicBlock
import hr.fer.decotlin.CFNode
import hr.fer.decotlin.Context
import hr.fer.decotlin.IntermediateCode
import java.util.*

class Generator(private val ctx: Context) {
    var structuringOrder: List<BasicBlock> = mutableListOf<BasicBlock>()

    fun run(cfgMin: CFNode): String {
        // Search for marked blocks

        /* val headerIntermediateCode: IntermediateCode = IntermediateCode(ctx)
        // BFS, search for any loop headers
        val queue = LinkedList<CFNode>()
        queue.add(cfgMin)
        val visited = mutableListOf<CFNode>()
        var parentNode: CFNode = cfgMin
        while (!queue.isEmpty()) {
            val current = queue.pop()

            // Cycle prevention
            if (!visited.contains(current)) visited.add(current)
            else continue

            if (current.block.isHeader()) {
                parentNode = current
                break
            }

            // Continue
            if (current.left != null) queue.add(current.left!!)
            if (current.right != null) queue.add(current.right!!)
        }
        var lhStructured = getLoopHeader(headerIntermediateCode, parentNode) */

        // By access flags generated class and its modifiers

        // iterate through all methods and their CFGs
        // Determine their method names by javaClass.method[index].toString()

        val decompiledSource = restructureCFG(cfgMin)

        println("Source generated.")
        return decompiledSource
    }

    private fun restructureCFG(rootNode: CFNode): String {
        // Order the blocks by their key values, in case without optimization
        // BFS
        val queue = LinkedList<CFNode>()
        queue.add(rootNode)
        val visited = mutableListOf<CFNode>()
        var current: CFNode
        while (!queue.isEmpty()) {
            current = queue.pop()

            // Check if visited for cycle prevention
            if (!visited.contains(current)) visited.add(current)
            else continue

            // Add children
            if (current.left != null) {
                queue.add(current.left!!)
            }
            if (current.right != null) {
                queue.add(current.right!!)
            }
        }
        // Restructure
        var content = ""
        for (node in visited.sortedBy { it.key }) {
            for ((byteIndex, instr) in node.block.code.entries) {
                content += "$byteIndex\t$instr\n"
            }
        }
        return content
    }

    // Determine marked blocks semantics
    // Before the method call drop the expression from the last block (merge)
    fun getLoopHeader(loopHeader: IntermediateCode, previousNode: CFNode): String {
        val header = loopHeader.getCode()[0]?.split("if (")?.get(1)?.split(") goto")?.get(0) ?: return ""

        var comparatorWithWhitespace: String = ""
        // Equality
        if (header.contains("==")) {
            comparatorWithWhitespace = " == "
        }
        // Lesser
        else if (header.contains("<")) {
            comparatorWithWhitespace = " < "
        }
        // Lesser or equal
        else if (header.contains("<=")) {
            comparatorWithWhitespace = " <= "
        }
        // Greater
        else if (header.contains(">")) {
            comparatorWithWhitespace = " > "
        }
        // Greater or equal
        else if (header.contains(">=")) {
            comparatorWithWhitespace = " >= "
        }
        val loopVar = header.split(comparatorWithWhitespace)[0]
        val startValue: String? = previousNode.block.code[previousNode.key - 1]
        val endValue = header.split(comparatorWithWhitespace)[1]

        // Format the loop header
        return "for ($loopVar in $startValue..$endValue)"
    }
}
