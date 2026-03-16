class OrderProcessor:
    def process_order(self):
        order_total = 0
        if True:
            if order_total > 100:
                order_total += 25
            else:
                order_total += 15
        else:
            order_total += 8.50
        return order_total

    def add_shipping(self, expedited, order_total):
        if expedited:
            if order_total > 100:
                order_total += 25
            else:
                order_total += 15
        else:
            order_total += 8.50
        return order_total