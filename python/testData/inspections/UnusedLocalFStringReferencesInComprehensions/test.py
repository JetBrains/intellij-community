def f():
    [f'{x1}' for x1 in range(10)]
    [[f'{x2}' for _ in range(10)] for x2 in range(10)]
    [[42 for _ in range(10) if f'{x3}'] for x3 in range(10)]

    [42 for x4 in range(10) if f'{x4}']
    [42 for x5 in range(10) if [f'{x5}' for _ in range(10)]]
    [42 for x6 in range(10) if [42 for _ in range(10) if f'{x6}']]

    [f'{x7}' for _ in range(10) for x7 in range(10)]