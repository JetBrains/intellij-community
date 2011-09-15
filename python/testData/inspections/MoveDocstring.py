class GetCustomerPaymentProfileRequest(CustomerRequest):
  _keys = CustomerRequest._keys + ["customerPaymentProfileId"]
  <warning descr="Docstring seems to be misplaced">"""
  Gets a payment profile by user <caret>Account object and authorize.net
  profileid of the payment profile.
  """</warning>
  def __init__(self, user, profileid):
    CustomerRequest.__init__(self, user,
                               customerPaymentProfileId=profileid)