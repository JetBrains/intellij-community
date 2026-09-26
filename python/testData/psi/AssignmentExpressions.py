if a := b:
    pass

while c := d:
   pass

[y := 2, y**2, y**3]

[y for x in data if (y := f(x))]

len(lines := [])

foo(x := 1, cat='2')

(loc := x, y)  # loc will be x, not (x, y)

(px, py, pz := position)  # pz will be position, px and py are references

x = (y := z) = 'spam'  # z is a reference

result_list = [a := 1]
result_list = [(a := 1)]