handler = webapp2.WSGIApplication([
    ('/', UserHandler),
    ()
], debug=True)
