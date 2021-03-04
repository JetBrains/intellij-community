[i for i in (<error descr="Assignment expression cannot be used in a comprehension iterable">x := (1, 2)</error>)]

[[x for x in range(<error descr="Assignment expression cannot be used in a comprehension iterable">z := 10</error>)] for j in range(10)]