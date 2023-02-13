0	v0 = 100
1	v1 = 55
2	v2 = 1
3	if (v2 >= 6) goto 10
4	if (v0 * java.lang.Math.random() <= v1) goto 7
5	java.lang.System.out.println("Above average individual")
6	goto 8
7	java.lang.System.out.println("Below average individual")
8	v2 = v2 + 1
9	goto 3
