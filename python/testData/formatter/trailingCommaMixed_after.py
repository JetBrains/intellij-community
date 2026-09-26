def calculate_statistics(
        numbers,
        method="mean",
):
    return None


stats_data = {
    "datasets": [
        [1, 2, 3, 4],
        [5, 6, 7],
        [8, 9],
    ],
    "names": (
        "Dataset A",
        "Dataset B",
        "Dataset C",
    ),
    "results": {
        "Dataset A": calculate_statistics(
            numbers=[1, 2, 3, 4],
            method="mean",
        ),
        "Dataset B": calculate_statistics(
            numbers=[5, 6, 7],
            method="sum",
        ),
    },
    "metadata": {
        "author": "John Doe",
        "created_at": (
            2025,
            1,
            15,
        ),
        "tags": [
            "statistics",
            "math",
        ],
    },
}

mean_values = [
    calculate_statistics(
        numbers=data,
        method="mean",
    )
    for data in stats_data["datasets"]
]
