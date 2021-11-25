from warehouse import Warehouse

warehouse = Warehouse()
warehouse.add_fruits('peach', 3)
warehouse.add_fruits('pineapple', 5)
warehouse.add_fruits('mango', 1)
warehouse.add_fruits('apple', 5)

result = warehouse.take_fruit('apple')
if result:
    print('This apple was delicious!')

warehouse.print_all_fruits()
