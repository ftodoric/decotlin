package hr.fer.decotlin

import DecompilerConfig
import hr.fer.decotlin.m01.Parser
import hr.fer.decotlin.m02.UDM
import hr.fer.decotlin.m03.Generator

fun main() {
    // Decompiler config
    val config = DecompilerConfig("C:\\Workspace\\decotlin\\src\\main\\resources\\decotlin.config")

    // Decompiler context
    val ctx = Context(config)

    // 1st phase
    val parser = Parser(ctx)
    val targetClass = config.getConfig("targetClass") ?: return
    val cfg = parser.run(targetClass)

    // 2nd phase
    val udm = UDM(ctx)
    val cfgMin = udm.run(cfg)

    // 3rd phase
    val generator = Generator(ctx)
    val decompiledSource: String = generator.run(cfgMin)
    ctx.targetFile.writeText(decompiledSource)
}
