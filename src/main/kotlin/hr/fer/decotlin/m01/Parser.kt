package hr.fer.decotlin.m01

import hr.fer.decotlin.*
import org.apache.bcel.Const
import org.apache.bcel.classfile.ClassParser

enum class JumpType {
    Entry,
    Exit,
    Bounce,
    None
}

/**
 * 1st module - Parser
 */
class Parser(private val ctx: Context) {
    private val basicBlockMapping: MutableMap<Int, JumpType> = mutableMapOf()
    private val jumpEntryIndices: MutableList<Int> = mutableListOf()
    private val jumpTable: MutableMap<Int, Int> = mutableMapOf()

    /**
     * @return list of control flow graphs
     * Each CFG represents intermediate code of a single method.
     * For now, only main programs are decompiled correctly.
     */
    fun run(classPath: String): CFNode {
        val cp = ClassParser(classPath)
        val classFile = cp.parse()

        // Save class file information to the context
        ctx.setClassFile(classFile)

        // Iterate through all methods and create a CFG for each one
        val intermediateCodes = mutableListOf<IntermediateCode>()
        var intermediateDump: String = ""
        for (method in classFile.methods) {
            // Iterate through bytecode of the current method
            var byteIndex = 0
            val irc = IntermediateCode(ctx)
            var opcode: Short
            var nextBytesCount: Short
            while (byteIndex < method.code.code.size) {
                // Determine opcode
                opcode = method.code.code[byteIndex].toUByte().toShort()

                // Determine next bytes
                nextBytesCount = getNextBytesCount(opcode)

                // Params
                val opcodeParams: MutableList<Byte> = mutableListOf()
                for (i in 1..nextBytesCount) {
                    if (byteIndex + i == method.code.code.size) break
                    opcodeParams.add(method.code.code[byteIndex + i])
                }

                // Transform into intermediate code
                irc.applyOpcode(byteIndex, opcode, opcodeParams)

                // Increase byteIndex appropriately
                byteIndex += nextBytesCount + 1
            }
            intermediateCodes.add(irc)
            intermediateDump += "$method\n"
            intermediateDump += "$irc\n"
        }

        // Copy propagation
        val intermediateCode = intermediateCodes[0]
        val stackVars: MutableMap<String, String> = mutableMapOf()
        while (isReducible(intermediateCode)) {
            for ((byteIndex, instr) in intermediateCode.getCode().entries) {
                if (!instr.contains("=") || instr.contains("goto")) continue
                val assign = instr.split(" = ")
                val leftSide = assign[0]
                var rightSide = assign[1]
                if (leftSide.contains("s")) stackVars.put(leftSide, rightSide)
                // reversed to find the longest match of the replace
                for ((key, value) in stackVars.entries.reversed()) {
                    rightSide = rightSide.replace(key, value)
                }
                intermediateCode.set(byteIndex, "$leftSide = $rightSide")
            }
        }

        // Go through cond instructions and replace stack variables in the condition
        for ((byteIndex, instr) in intermediateCode.getCode().entries) {
            if (instr.contains("goto")) {
                // reversed to find the longest match of the replace
                var newInstr = instr
                for ((key, value) in stackVars.entries.reversed()) {
                    newInstr = newInstr.replace(key, value)
                }
                intermediateCode.set(byteIndex, newInstr)
            }
            // Void Methods
            if (!instr.contains("=") && instr.contains(".")) {
                var newInstr = instr
                for ((key, value) in stackVars.entries.reversed()) {
                    newInstr = newInstr.replace(key, value)
                }
                intermediateCode.set(byteIndex, newInstr)
            }
        }

        // Remove all stack variables and adjust branching offsets
        intermediateCode.removeStackVar()

        // Adjust branching offsets
        val newIntermediateCode: MutableMap<Int, String> = mutableMapOf()
        val branchOffsetMapping: List<Int> = intermediateCode.getCode().toSortedMap().keys.toList()
        for ((byteIndex, instr) in intermediateCode.getCode().entries) {
            var newInstr = instr
            if (instr.contains("goto")) {
                val branchOffset = instr.split("goto ")[1].toInt()
                var newBranchOffset = 0
                for ((index, byteIndex) in branchOffsetMapping.withIndex()) {
                    if (byteIndex >= branchOffset) {
                        newBranchOffset = index
                        break
                    }
                }
                newInstr = instr.split("goto ")[0] + "goto $newBranchOffset"
            }
            newIntermediateCode.put(branchOffsetMapping.indexOf(byteIndex), newInstr)
        }
        intermediateCode.setCode(newIntermediateCode)

        // Create list of entries for the CFG, basic blocks generation
        for ((byteIndex, instr) in intermediateCodes[0].getCode()) {
            basicBlockMapping[byteIndex] = JumpType.None
            if (byteIndex == 0) markBlockEntry(byteIndex)
            if (hasJump(instr)) {
                val jumpDest = getJumpDest(instr)
                jumpEntryIndices.add(jumpDest)
                markBlockExit(byteIndex)
                jumpTable[byteIndex] = jumpDest
            }
            if (byteIndex == intermediateCodes[0].getCode().size - 1) {
                markBlockExit(byteIndex)
            }
        }

        for (byteIndex in jumpEntryIndices) {
            markBlockEntry(byteIndex)
        }

        // Before every entry, mark exit
        for (index in basicBlockMapping.keys) {
            if (isEntryMarked(index + 1)) markBlockExit(index)
        }

        // After every exit, mark entry
        for (index in basicBlockMapping.keys) {
            if (isExitMarked(index)
                && index != basicBlockMapping.size - 1
            ) markBlockEntry(index + 1)
        }

        // Extract block starting indices
        val blockStarts: MutableList<Int> = mutableListOf()
        for (byteIndex in basicBlockMapping.keys) {
            if (isEntryMarked(byteIndex)) {
                blockStarts.add(byteIndex)
            }
        }
        // Generate all basic blocks with their respective code and their nodes
        val code = intermediateCode.getCode()
        val nodes: MutableList<CFNode> = mutableListOf()
        for ((index, blockStart) in blockStarts.withIndex()) {
            val basicBlockCode: MutableMap<Int, String> = mutableMapOf()
            val blockEnd =
                if (index == blockStarts.size - 1) intermediateCode.getCode().size - 1 else blockStarts[index + 1]
            for (i in blockStart until blockEnd) {
                code[i]?.let { basicBlockCode.put(i, it) }
            }
            nodes.add(CFNode(BasicBlock(basicBlockCode, jumpTable[blockEnd - 1]), index))
        }

        // Create CFG
        // Use branchOffsetMapping to correctly cross reference all the nodes
        for ((index, node) in nodes.withIndex()) {
            // Left node designates a case when control flow condition evaluates to false (no jump)
            if (index + 1 <= nodes.size - 1) {
                var isConditionalJump = true
                for (instr in node.block.code.values) {
                    if (instr.contains("goto") && !instr.contains("if (")) {
                        isConditionalJump = false
                        break
                    }
                }
                if (isConditionalJump) node.left = nodes[index + 1]
            }
            // Right node designates a case when control flow condition evaluates to true (jump)
            val blockReference = node.block.jumpTo
            // Block reference points to the byte index
            // Now find the block that contains that byte index
            if (blockReference != null) {
                for (targetNode in nodes) {
                    if (targetNode.block.containsByteIndex(blockReference)) node.right = targetNode
                }
            }
        }
        val rootNode = nodes[0]

        return rootNode
    }

    fun isReducible(code: IntermediateCode): Boolean {
        for (instr in code.getCode().values) {
            if (!instr.contains("=") || instr.contains("goto")) continue
            val rightSide = instr.split(" = ")[1]
            if (rightSide.contains("""s\d+""".toRegex())) return true
        }
        return false
    }

    fun markBlockEntry(byteIndex: Int) {
        if (basicBlockMapping[byteIndex] == JumpType.Exit) {
            basicBlockMapping[byteIndex] = JumpType.Bounce
        } else if (basicBlockMapping[byteIndex] != JumpType.Bounce)
            basicBlockMapping[byteIndex] = JumpType.Entry
    }

    fun markBlockExit(byteIndex: Int) {
        if (basicBlockMapping[byteIndex] == JumpType.Entry)
            basicBlockMapping[byteIndex] = JumpType.Bounce
        else if (basicBlockMapping[byteIndex] != JumpType.Bounce)
            basicBlockMapping[byteIndex] = JumpType.Exit
    }

    fun isEntryMarked(byteIndex: Int): Boolean {
        return basicBlockMapping[byteIndex] == JumpType.Entry || basicBlockMapping[byteIndex] == JumpType.Bounce
    }

    fun isExitMarked(byteIndex: Int): Boolean {
        return basicBlockMapping[byteIndex] == JumpType.Exit || basicBlockMapping[byteIndex] == JumpType.Bounce
    }

    fun hasJump(instr: String): Boolean {
        val keywords = listOf("goto")
        for (keyword in keywords) {
            if (instr.contains(keyword)) return true
        }
        return false
    }

    fun getJumpDest(instr: String): Int {
        return instr.split("goto ")[1].toInt()
    }

    fun getCFGContent(nodes: MutableList<CFNode>): String {
        var cfgContent = ""
        for ((index, node) in nodes.withIndex()) {
            cfgContent += "BLOCK $index\n${node.block}\n"
            cfgContent += "LEFT\n${node.left?.block}\n"
            cfgContent += "RIGHT\n${node.right?.block}\n"
            cfgContent += "\n================================\n\n"
        }
        return cfgContent
    }

    private fun getNextBytesCount(opcode: Short): Short {
        return when (opcode) {
            // Instructions with 1 additional byte
            Const.ALOAD,
            Const.ASTORE,
            Const.BIPUSH,
            Const.DLOAD,
            Const.DSTORE,
            Const.FLOAD,
            Const.FSTORE,
            Const.ILOAD,
            Const.ISTORE,
            Const.LDC,
            Const.LLOAD,
            Const.LSTORE,
            Const.NEWARRAY,
            Const.RET
            -> 1

            // Instructions with 2 additional bytes
            Const.ANEWARRAY,
            Const.CHECKCAST,
            Const.GETFIELD,
            Const.GETSTATIC,
            Const.GOTO,
            Const.IF_ACMPEQ,
            Const.IF_ACMPNE,
            Const.IF_ICMPEQ,
            Const.IF_ICMPGE,
            Const.IF_ICMPGT,
            Const.IF_ICMPLE,
            Const.IF_ICMPLT,
            Const.IF_ICMPNE,
            Const.IFEQ,
            Const.IFGE,
            Const.IFGT,
            Const.IFLE,
            Const.IFLT,
            Const.IFNE,
            Const.IFNONNULL,
            Const.IFNULL,
            Const.IINC,
            Const.INSTANCEOF,
            Const.INVOKESPECIAL,
            Const.INVOKESTATIC,
            Const.INVOKEVIRTUAL,
            Const.LDC_W,
            Const.LDC2_W,
            Const.NEW,
            Const.PUTFIELD,
            Const.PUTSTATIC,
            Const.SIPUSH
            -> 2

            // Instructions with 3 additional bytes
            Const.MULTIANEWARRAY
            -> 3

            // Instructions with 4 additional bytes
            Const.GOTO_W,
            Const.INVOKEDYNAMIC,
            Const.INVOKEINTERFACE
            -> 4

            // Instructions with no additional bytes
            else -> 0
        }
    }
}
