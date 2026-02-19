from util import FRUITS


class Warehouse:
    # Fruit name to amount of it in warehouse
    entry = {}  # Apple, banana, etc...

    def __init__(self):
        for fruit in FRUITS:
            self.entry[fruit] = 0

    # fruit name from util.FRUITS (mango, apple...)
    def add_fruits(self, fruit_name, quantity):
        cur_quantity = self.entry.get(fruit_name)
        if cur_quantity is not None:
            self.entry[fruit_name] = cur_quantity + quantity
        else:
            raise KeyError("Not found fruit with name: " + fruit_name)

    def take_fruit(self, fruit_name):
        cur_quantity = self.entry.get(fruit_name)
        if cur_quantity is None:
            raise KeyError("Not found fruit with name: " + fruit_name)
        elif cur_quantity > 0:
            self.entry[fruit_name] = cur_quantity - 1
            return True
        return False

    def print_all_fruits(self):
        for fruit, quantity in self.entry.items():
            print(fruit + ": " + str(quantity))
