class GetCustomerPaymentProfileRequest(CustomerRequest):
  """
  Gets a payment profile by user Account object and authorize.net
  profileid of the payment profile.
  """

  def __init__(self, user, profileid):
    CustomerRequest.__init__(self, user,
                             customerPaymentProfileId=profileid)
