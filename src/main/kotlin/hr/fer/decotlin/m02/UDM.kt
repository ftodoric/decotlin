package hr.fer.decotlin.m02

import hr.fer.decotlin.BasicBlock
import hr.fer.decotlin.CFNode
import hr.fer.decotlin.Context
import hr.fer.decotlin.IntermediateCode
import java.util.*

class UDM(private val ctx: Context) {
    fun run(cfg: CFNode): CFNode {
        val graph: MutableMap<Int, List<Int>> = cfg.compactGraph()

        // Find dominance features
        // val (dominators, frontier) = findDominatingFeatures(graph, cfg.key)

        // Control Header optimization - conditional statements
        headersOptimization(cfg)

        ctx.targetCFGFile.writeText(cfg.toString())

        val cfgMin = cfg
        return cfgMin
    }

    // graph contains all the vertices/nodes with their respective keys
    // Contains Lenguaer-Tarjan algorithm for removing cycles in directed cyclic graph
    private fun findDominatingFeatures(
        cfg: MutableMap<Int, List<Int>>,
        startNode: Int
    ): Pair<Map<Int, List<Int>>, Map<Int, Set<Int>>> {
        val n = cfg.keys.size
        val idom = IntArray(n) { -1 }
        val sdom = IntArray(n)
        val parent = IntArray(n)
        val semi = IntArray(n)
        val label = IntArray(n)
        val dfn = IntArray(n)
        val vertex = IntArray(n + 1)
        val bucket = Array(n) { mutableListOf<Int>() }
        val dsu = IntArray(n) { -1 }
        var dfsNum = 0

        fun dfs(v: Int) {
            dfn[v] = ++dfsNum
            vertex[dfsNum] = v
            semi[v] = dfn[v]
            for (u in cfg[v]!!) {
                if (dfn[u] == 0) {
                    parent[u] = v
                    dfs(u)
                }
                bucket[u].add(v)
            }
        }

        dfs(startNode)

        for (i in dfsNum downTo 2) {
            val w = vertex[i]
            for (v in bucket[w]) {
                val u = semi[v]
                if (dfn[u] < dfn[semi[v]]) semi[v] = u
            }
            if (semi[w] <= bucket.size - 1) bucket[semi[w]].add(w)
            dsu[w] = semi[w]
            var x = parent[w]
            for (v in bucket[x]) {
                val u = semi[v]
                if (dfn[u] < dfn[semi[v]]) semi[v] = u
                if (semi[v] == semi[w]) idom[v] = w
                else idom[v] = u
            }
            bucket[x].clear()
        }

        for (i in 1 until dfsNum) {
            val w = vertex[i]
            if (idom[w] != semi[w] && idom[w] != -1) idom[w] = idom[idom[w]]
        }

        val result = mutableMapOf<Int, MutableList<Int>>()
        for (i in 0 until n) {
            if (idom[i] != -1) {
                if (result[idom[i]] == null) {
                    result[idom[i]] = mutableListOf(i)
                } else {
                    result[idom[i]]!!.add(i)
                }
            }
        }

        val frontiers: MutableMap<Int, Set<Int>> = mutableMapOf()
        /* for ((key, idom) in result.entries) {
            frontiers[key] = dominanceFrontier(graph, idom.toIntArray(), startNode)
        }*/
        return Pair(result, frontiers)
    }

    // Search for specific patterns that can be replaced
    // E.g. intermediate result of the dcmpl instruction
    private fun headersOptimization(rootNode: CFNode) {
        val visited = mutableListOf<CFNode>()
        val queue = LinkedList<CFNode>()
        // BFS queue
        queue.add(rootNode)
        var current: CFNode
        while (!queue.isEmpty()) {
            current = queue.pop()

            // Check if visited for cycle prevention
            if (!visited.contains(current)) visited.add(current)
            else continue

            // Search for headers
            for ((instrIndex, instr) in current.block.code.entries) {
                if (!instr.contains("if (")) continue

                val condExpr = instr.split("if (")[1].split(") goto")[0]
                val cond = "(v0 * java.lang.Math.random() - v1)/|v0 * java.lang.Math.random() - v1| <= 0"

                // dcmpl opt
                if (condExpr.contains("""\)/\|""".toRegex())) {
                    val expr = condExpr.split("/|")[1].split("|")[0]
                    val var1 = expr.split(" - ")[0]
                    val var2 = expr.split(" - ")[1]
                    val comparator = condExpr.split("| ")[1].substring(0, 2)
                    val branchOffset = instr.split("if (")[1].split(") goto")[1]
                    val newCondExpr = "if ($var1 $comparator $var2) goto$branchOffset"
                    current.block.code[instrIndex] = newCondExpr
                }
            }

            // Add next children, BFS
            if (current.left != null) queue.add(current.left!!)
            if (current.right != null) queue.add(current.right!!)
        }
    }

    fun getDomSet(node: CFNode): MutableSet<Int> {
        val dom: MutableSet<Int> = mutableSetOf()
        val visited: MutableList<CFNode> = mutableListOf()
        val current = node
        while (!visited.contains(current)) {
            visited.add(current)
        }
        return dom
    }

    fun getDFronts(dominators: Map<Int, List<Int>>) {
        var frontiers: MutableMap<Int, Set<Int>> = mutableMapOf()
    }

    fun dominanceFrontier(graph: MutableMap<Int, List<Int>>, idom: IntArray, node: Int): Set<Int> {
        val frontier = mutableSetOf<Int>()
        for (y in graph[node]!!) {
            if (idom[y] != node) frontier.add(y)
            else for (z in dominanceFrontier(graph, idom, y)) frontier.add(z)
        }
        return frontier
    }

    // Simple forward data flow algorithm that computes the transfer
    // function for each CF node
    fun forwardDataFlow(cfg: Map<Int, List<Int>>, transferFunction: (Int) -> Int) {
        val nodes = cfg.keys.toList().sorted()
        val nodeValues = MutableList(nodes.size) { 0 }
        for (node in nodes) {
            nodeValues[node] = transferFunction(node)
            for (child in cfg[node]!!) {
                nodeValues[child] = nodeValues[child].coerceAtLeast(nodeValues[node])
            }
        }
    }

    // Transfer functions for reaching definitions
    fun computeInReachDefs(parentOutReachDefs: List<Set<MutableMap<Int, List<String>>>>): Set<MutableMap<Int, List<String>>> {
        val resultingSet: Set<MutableMap<Int, List<String>>>
        resultingSet = parentOutReachDefs.reduce { acc, parent -> acc union parent }
        return resultingSet
    }

    // Out reach of a CF node
    fun computeOutReachDefs(
        thisInReachDefs: Set<MutableMap<Int, List<String>>>,
        thisNewDefs: Set<MutableMap<Int, List<String>>>,
        thisOverwritingDefs: Set<MutableMap<Int, List<String>>>
    ): Set<MutableMap<Int, List<String>>> {
        val resultingSet: Set<MutableMap<Int, List<String>>>
        resultingSet = thisNewDefs union (thisInReachDefs subtract thisOverwritingDefs)
        return resultingSet
    }
}
