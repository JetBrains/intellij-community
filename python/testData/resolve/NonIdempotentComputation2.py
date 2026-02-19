costs = [[17, 2, 17], [16, 16, 5], [14, 3, 19]]

color_range = range(len(costs[0]))

prev = costs[0]

for house in range(1, len(costs)):
    current = [
        costs[house][color] + min(prev[:color] + prev[color + 1 :])
        #                             <ref1>         <ref2>
        for color in color_range
    ]
    prev = current

print(min(prev))