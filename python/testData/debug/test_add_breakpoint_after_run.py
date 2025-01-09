import time


def calculate_sum(a, b):
    # Use a breakpoint here to inspect the program flow
    result = a + b
    return result


def greet(name):
    # Use a breakpoint here to debug the call
    print(f"Hello, {name}!")


if __name__ == "__main__":
    counter = 0

    while True:
        print("\nIteration:", counter)  # You can set a breakpoint here while the loop runs
        num1 = counter
        num2 = counter + 1
        sum_result = calculate_sum(num1, num2)  # Step into this function during debugging
        print(f"The sum of {num1} and {num2} is: {sum_result}")
        greet("Debugger")  # Also step into this function if needed
        counter += 1
        # Add a sleep to slow down the loop and make debugging easier
        time.sleep(2)