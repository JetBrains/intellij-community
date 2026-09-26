from package_a.main import Package_A_class

class Package_B_class:
    def __init__(self):
        print("Hello from package_b init!")
        result_b = "package_b executed successfully initiated"
        print(result_b)

if __name__ == "__main__":
    a = Package_A_class()
    b = Package_B_class()

