class GetCustomerPaymentProfileRequest(CustomerRequest):
  def __init__(self, user, profileid):
    CustomerRequest.__init__(self, user,
                             customerPaymentProfileId=profileid)
  <warning descr="Docstring seems to be misplaced">"""
  Gets a payment profile by user <caret>Account object and authorize.net
  profileid of the payment profile.
  """</warning>
