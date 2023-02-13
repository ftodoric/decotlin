package hr.fer.decotlin

import org.apache.bcel.Const

/**
 * One instance of this class refers to an instruction sequence of one method.
 */
class IntermediateCode(private val ctx: Context) {
    // Every line of the code has a byte index
    private var code: MutableMap<Int, String> = mutableMapOf()
    private var stackVarIndex: Int = 0
    private val argTypes = listOf("D", "Object")

    fun applyOpcode(byteIndex: Int, opcode: Short, params: MutableList<Byte>) {
        val constant: Int
        val branchOffset: Int
        if (opcode == Const.BIPUSH) {
            constant = params[0].toInt()
            code[byteIndex] = "s$stackVarIndex = $constant"
            stackVarIndex++
        } else if (opcode == Const.ISTORE_0) {
            code[byteIndex] = "v0 = s${stackVarIndex - 1}"
        } else if (opcode == Const.ISTORE_1 || opcode == Const.DSTORE_1) {
            code[byteIndex] = "v1 = s${stackVarIndex - 1}"
        } else if (opcode == Const.ISTORE_2 || opcode == Const.DSTORE_2) {
            code[byteIndex] = "v2 = s${stackVarIndex - 1}"
        } else if (opcode == Const.ISTORE_3 || opcode == Const.DSTORE_3) {
            code[byteIndex] = "v3 = s${stackVarIndex - 1}"
        } else if (opcode == Const.ICONST_1) {
            code[byteIndex] = "s$stackVarIndex = 1"
            stackVarIndex++
        } else if (opcode == Const.ICONST_2) {
            code[byteIndex] = "s$stackVarIndex = 2"
            stackVarIndex++
        } else if (opcode == Const.ICONST_3) {
            code[byteIndex] = "s$stackVarIndex = 3"
            stackVarIndex++
        } else if (opcode == Const.ICONST_4) {
            code[byteIndex] = "s$stackVarIndex = 4"
            stackVarIndex++
        } else if (opcode == Const.ICONST_5) {
            code[byteIndex] = "s$stackVarIndex = 5"
            stackVarIndex++
        } else if (opcode == Const.ILOAD_0) {
            code[byteIndex] = "s$stackVarIndex = v0"
            stackVarIndex++
        } else if (opcode == Const.ILOAD_1 || opcode == Const.DLOAD_1) {
            code[byteIndex] = "s$stackVarIndex = v1"
            stackVarIndex++
        } else if (opcode == Const.ILOAD_2 || opcode == Const.DLOAD_2) {
            code[byteIndex] = "s$stackVarIndex = v2"
            stackVarIndex++
        } else if (opcode == Const.ILOAD_3 || opcode == Const.DLOAD_3) {
            code[byteIndex] = "s$stackVarIndex = v3"
            stackVarIndex++
        } else if (opcode == Const.IF_ICMPGE) {
            // Construct branching offset from params
            branchOffset = composeBranchOffset(byteIndex, params)
            code[byteIndex] = "if (s${stackVarIndex - 2} >= s${stackVarIndex - 1}) goto $branchOffset"
        } else if (opcode == Const.IFLE) {
            // Construct branching offset from params
            branchOffset = composeBranchOffset(byteIndex, params)
            code[byteIndex] = "if (s${stackVarIndex - 1} <= 0) goto $branchOffset"
        } else if (opcode == Const.RETURN) {
            code[byteIndex] = "return"
        } else if (opcode == Const.INVOKESTATIC) {
            // Get pool ref
            val cp = ctx.getClassFile().constantPool
            // Generate index from instruction bytes
            val cpIndex = (params[0].toUByte().toInt() shl 8) or params[1].toUByte().toInt()
            val funSignature = cp.constantToString(cp.getConstant(cpIndex))
            val method = funSignature.split(" (")[0]
            val args = funSignature.split("(")[1].split(")")[0]
            val numOfArgs = args.split(";").size
            var paramsString = ""
            for (i in 1 until numOfArgs) {
                paramsString += "s${stackVarIndex - i}"
                if (i != numOfArgs - 1) paramsString += ", "
            }
            code[byteIndex] = "s$stackVarIndex = $method($paramsString)"
            stackVarIndex++
        } else if (opcode == Const.GETSTATIC) {
            // Get pool ref
            val cp = ctx.getClassFile().constantPool
            // Generate index from instruction bytes
            val cpIndex = (params[0].toUByte().toInt() shl 8) or params[1].toUByte().toInt()
            val refSignature = cp.constantToString(cp.getConstant(cpIndex))
            val obj = refSignature.split(" ")[0] // callee of interest
            code[byteIndex] = "s$stackVarIndex = $obj"
            stackVarIndex++
        } else if (opcode == Const.INVOKEVIRTUAL) {
            // Get pool ref
            val cp = ctx.getClassFile().constantPool
            // Generate index from instruction bytes
            val cpIndex = (params[0].toUByte().toInt() shl 8) or params[1].toUByte().toInt()
            val funSignature = cp.constantToString(cp.getConstant(cpIndex))
            val returnType = funSignature.split(")")[1]
            val temp = funSignature.split(" (")[0].split(".")
            val method = temp[temp.size - 1]
            val args = funSignature.split("(")[1].split(")")[0]
            var numOfArgs = 0
            for (argType in argTypes) {
                if (args.contains(argType)) numOfArgs++
            }
            var paramsString = ""
            for (i in 0 until numOfArgs) {
                paramsString += "s${stackVarIndex - 1 - i}"
                if (i != numOfArgs - 1) paramsString += ", "
            }
            // Return type is void
            if (returnType == "V") {
                code[byteIndex] = "s${stackVarIndex - 1 - numOfArgs}.$method($paramsString)"
            } else {
                code[byteIndex] = "s$stackVarIndex = s${stackVarIndex - 1 - numOfArgs}.$method($paramsString)"
                stackVarIndex++
            }
        } else if (opcode == Const.DMUL) {
            code[byteIndex] = "s${stackVarIndex} = s${stackVarIndex - 2} * s${stackVarIndex - 1}"
            stackVarIndex++
        } else if (opcode == Const.DCMPL) {
            code[byteIndex] =
                "s${stackVarIndex} = (s${stackVarIndex - 2} - s${stackVarIndex - 1})/|s${stackVarIndex - 2} - s${stackVarIndex - 1}|"
            stackVarIndex++
        } else if (opcode == Const.LDC) {
            // Get pool ref
            val cp = ctx.getClassFile().constantPool
            val cpIndex = params[0].toUByte().toInt()
            val loadedString = cp.constantToString(cp.getConstant(cpIndex))
            code[byteIndex] = "s$stackVarIndex = $loadedString"
            stackVarIndex++
        } else if (opcode == Const.SWAP) {
            code[byteIndex] = "s$stackVarIndex = s${stackVarIndex - 1}"
            ++stackVarIndex
            // Any added instructions will not contain byteIndex, because their offsets aren't relevant
            code[-1 - byteIndex] = "s$stackVarIndex = s${stackVarIndex - 3}"
            stackVarIndex++
        } else if (opcode == Const.GOTO) {
            // Construct branching offset from params
            branchOffset = composeBranchOffset(byteIndex, params)
            code[byteIndex] = "goto $branchOffset"
        } else if (opcode == Const.IINC) {
            val localIndex = params[0]
            val incValue = params[1]
            code[byteIndex] = "v$localIndex = v$localIndex + $incValue"
        } else if (opcode == Const.DSUB) {
            code[byteIndex] = "s$stackVarIndex = s${stackVarIndex - 2} - s${stackVarIndex - 1}"
            stackVarIndex++
        }
    }

    fun composeBranchOffset(byteIndex: Int, params: MutableList<Byte>): Int {
        if (params.size == 2) return Math.abs((params[0] * 16 + params[1]) + byteIndex)
        else return params[0].toUByte().toInt() + byteIndex
    }

    fun getLength(): Int {
        return code.size
    }

    fun getCode(): MutableMap<Int, String> {
        return this.code
    }

    fun set(byteIndex: Int, newInstr: String) {
        this.code[byteIndex] = newInstr
    }

    fun setCode(code: MutableMap<Int, String>) {
        this.code = code
    }

    fun removeStackVar() {
        val indicesToRemove: MutableList<Int> = mutableListOf()
        for ((byteIndex, instr) in this.code.entries) {
            if (instr.contains("=") && !instr.contains("goto")) {
                var leftSide = instr.split(" = ")[0]
                if (leftSide.matches("""s\d+""".toRegex())) indicesToRemove.add(byteIndex)
            }
        }
        for (i in indicesToRemove) {
            this.code.remove(i)
        }
    }

    // Overrides
    override fun toString(): String {
        var result = ""
        code.forEach { entry ->
            if (entry.key >= 0) result += "${entry.key}"
            result += "\t${entry.value}\n"
        }
        return result
    }
}
