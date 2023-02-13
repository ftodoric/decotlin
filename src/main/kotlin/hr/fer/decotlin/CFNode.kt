package hr.fer.decotlin

import java.util.*

class CFNode(val block: BasicBlock, val key: Int) {
    var left: CFNode? = null
    var right: CFNode? = null

    fun find(byteIndex: Int): CFNode? = when {
        this.left != null -> left!!.find(byteIndex)
        this.right != null -> right!!.find(byteIndex)
        else -> {
            if (this.block.containsByteIndex(byteIndex)) this
            else null
        }
    }

    fun insertLeft(node: CFNode) {
        this.left = node
    }

    fun insertRight(node: CFNode) {
        this.right = node
    }

    private fun removeChildren(node: CFNode) {
        node.left = null
        node.right = null
    }

    private fun removeSingleChildNode(pos: String) {
        if (pos == "left") this.left = null else this.right = null
    }

    fun compactGraph(): MutableMap<Int, List<Int>> {
        // Traverse all nodes
        val graph: MutableMap<Int, List<Int>> = mutableMapOf()
        // BFS
        val queue = LinkedList<CFNode>()
        queue.add(this)
        val visited: MutableList<CFNode> = mutableListOf()
        while (!queue.isEmpty()) {
            val current = queue.pop()

            if (!visited.contains(current)) visited.add(current)
            else continue

            val currentChildren = mutableListOf<Int>()
            if (current.left != null) {
                currentChildren.add(current.left!!.key)
                queue.add(current.left!!)
            }
            if (current.right != null) {
                currentChildren.add(current.right!!.key)
                queue.add(current.right!!)
            }
            graph[current.key] = currentChildren
        }
        return graph
    }

    override fun toString(): String {
        var content = ""
        // BFS
        val queue = LinkedList<CFNode>()
        // Start with this node
        queue.add(this)
        val visited = mutableListOf<CFNode>()
        var current: CFNode
        while (!queue.isEmpty()) {
            current = queue.pop()

            // Check if visited for cycle prevention
            if (!visited.contains(current)) visited.add(current)
            else continue

            // Form the content
            content += "BLOCK ${current.key}\n"
            content += current.block.toString()
            content += "\nLEFT\n"
            content += current.left?.block.toString()
            content += if (current.left == null) "\n" else ""
            content += "\nRIGHT\n"
            content += current.right?.block.toString()
            content += if (current.right == null) "\n" else ""
            content += "\n===============================\n"

            // Add children
            if (current.left != null) {
                queue.add(current.left!!)
            }
            if (current.right != null) {
                queue.add(current.right!!)
            }
        }
        return content
    }
}
