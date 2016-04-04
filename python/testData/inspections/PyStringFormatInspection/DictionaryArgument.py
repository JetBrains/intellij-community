my_dict = {'class': 3}

my_dict['css_class'] = ""
if my_dict['class']:
    my_dict['css_class'] = 'class %(class)s' % my_dict

my_dict['tmp'] = 'classes %(css_class)s' % my_dict

my_dict['tmp'] = 'classes %(claz)s' % <warning descr="Key 'claz' has no following argument">my_dict</warning>
