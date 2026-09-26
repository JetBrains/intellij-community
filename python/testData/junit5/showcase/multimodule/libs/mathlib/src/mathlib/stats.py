import math

class FooBar:
    pass

def mean(data: list[float]) -> float:
    return sum(data) / len(data)



def median(data: list[float]) -> float:
    sorted_data = sorted(data)
    n = len(sorted_data)
    mid = n // 2
    if n % 2 == 0:
        return (sorted_data[mid - 1] + sorted_data[mid]) / 2
    return sorted_data[mid]


def stdev(data: list[float]) -> float:
    avg = mean(data)
    variance = sum((x - avg) ** 2 for x in data) / (len(data) - 1)
    return math.sqrt(variance)


class DataSeries:
    def __init__(self, name: str, values: list[float]) -> None:
        self.name = name
        self.values = values

    def mean(self) -> float:
        return mean(self.values)

    def median(self) -> float:
        return median(self.values)

    def stdev(self) -> float:
        return stdev(self.values)


class Histogram:
    def __init__(self, data: list[float], bins: int = 10) -> None:
        self.data = sorted(data)
        self.bins = bins

    def bucket_counts(self) -> list[tuple[float, float, int]]:
        lo, hi = self.data[0], self.data[-1]
        width = (hi - lo) / self.bins
        buckets: list[tuple[float, float, int]] = []
        for i in range(self.bins):
            edge = lo + width * i
            count = sum(1 for x in self.data if edge <= x < edge + width)
            buckets.append((edge, edge + width, count))
        return buckets
