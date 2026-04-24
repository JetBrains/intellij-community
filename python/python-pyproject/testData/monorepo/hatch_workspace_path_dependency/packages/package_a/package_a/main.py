# from package_b.main import Package_B_class

class Package_A_class:
    def __init__(self):
        print("Hello from package_a init!")
        result_a = "package_a executed successfully initiated"
        print(result_a)

if __name__ == "__main__":
    a = Package_A_class()
    # b = Package_B_class()