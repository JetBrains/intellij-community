# Handle a matched regex
if (<error descr="Python version 3.7 does not support assignment expressions">match := pattern.search(data)</error>) is not None:
    pass

# A loop that can't be trivially rewritten using 2-arg iter()
while <error descr="Python version 3.7 does not support assignment expressions">chunk := file.read(8192)</error>:
   pass

# Reuse a value that's expensive to compute
[<error descr="Python version 3.7 does not support assignment expressions">y := f(x)</error>, y**2, y**3]

# Share a subexpression between a comprehension filter clause and its output
filtered_data = [y for x in data if (<error descr="Python version 3.7 does not support assignment expressions">y := f(x)</error>) is not None]