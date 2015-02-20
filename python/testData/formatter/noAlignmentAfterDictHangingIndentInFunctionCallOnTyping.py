handler = webapp2.WSGIApplication([
    ('/', UserHandler),<caret>
], debug=True)
