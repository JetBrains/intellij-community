# Handle a matched regex
if (<warning descr="Python versions 2.7, 3.5, 3.6, 3.7 do not support assignment expressions">match := pattern.search(data)</warning>) is not None:
    pass

# A loop that can't be trivially rewritten using 2-arg iter()
while <warning descr="Python versions 2.7, 3.5, 3.6, 3.7 do not support assignment expressions">chunk := file.read(8192)</warning>:
   pass

# Reuse a value that's expensive to compute
[<warning descr="Python versions 2.7, 3.5, 3.6, 3.7 do not support assignment expressions">y := f(x)</warning>, y**2, y**3]

# Share a subexpression between a comprehension filter clause and its output
filtered_data = [y for x in data if (<warning descr="Python versions 2.7, 3.5, 3.6, 3.7 do not support assignment expressions">y := f(x)</warning>) is not None]