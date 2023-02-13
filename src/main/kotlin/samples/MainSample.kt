package samples

import java.lang.Math.random

fun main() {
    val maxScore = 100
    val averageScore = 55
    for (i in 1..5) {
        if (maxScore * random() > averageScore) println("Above average individual")
        else println("Below average individual")
    }
}
