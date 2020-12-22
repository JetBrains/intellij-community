import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Issue670 {
  @Delegate(excludes = ExcludesDelegateMethods.class)
  private PreferencesPayload prefPayload = new PreferencesPayload();

  private int someValue = 1<caret>;

  private interface ExcludesDelegateMethods {
    void setTacAccepted(boolean tacAccepted);

    void setUseAnimations(boolean useAnimations);

    void setCssTheme(int cssTheme);

    void setUserLanguage(String userLanguageCode);

    void setUserCountry(String userCountry);

    void setPreferredTradeCurrency(String preferredTradeCurrency);

    void setUseTorForBitcoinJ(boolean useTorForBitcoinJ);

    void setShowOwnOffersInOfferBook(boolean showOwnOffersInOfferBook);

    void setMaxPriceDistanceInPercent(double maxPriceDistanceInPercent);

    void setBackupDirectory(String backupDirectory);

    void setAutoSelectArbitrators(boolean autoSelectArbitrators);

    void setUsePercentageBasedPrice(boolean usePercentageBasedPrice);

    void setTagForPeer(String hostName, String tag);

    void setOfferBookChartScreenCurrencyCode(String offerBookChartScreenCurrencyCode);

    void setBuyScreenCurrencyCode(String buyScreenCurrencyCode);

    void setSellScreenCurrencyCode(String sellScreenCurrencyCode);

    void setIgnoreTradersList(List<String> ignoreTradersList);

    void setDirectoryChooserPath(String directoryChooserPath);

    void setTradeChartsScreenCurrencyCode(String tradeChartsScreenCurrencyCode);

    void setTradeStatisticsTickUnitIndex(int tradeStatisticsTickUnitIndex);

    void setSortMarketCurrenciesNumerically(boolean sortMarketCurrenciesNumerically);

    void setBitcoinNodes(String bitcoinNodes);

    void setUseCustomWithdrawalTxFee(boolean useCustomWithdrawalTxFee);

    void setWithdrawalTxFeeInBytes(long withdrawalTxFeeInBytes);

    void setSelectedPaymentAccountForCreateOffer(String paymentAccount);

    void setBsqBlockChainExplorer(String bsqBlockChainExplorer);

    void setPayFeeInBtc(boolean payFeeInBtc);

    void setFiatCurrencies(List<String> currencies);

    void setCryptoCurrencies(List<String> currencies);

    void setBlockChainExplorerTestNet(String blockChainExplorerTestNet);

    void setBlockChainExplorerMainNet(String blockChainExplorerMainNet);

    void setResyncSpvRequested(boolean resyncSpvRequested);

    void setDontShowAgainMap(Map<String, Boolean> dontShowAgainMap);

    void setPeerTagMap(Map<String, String> peerTagMap);

    void setBridgeAddresses(List<String> bridgeAddresses);

    void setBridgeOptionOrdinal(int bridgeOptionOrdinal);

    void setTorTransportOrdinal(int torTransportOrdinal);

    void setCustomBridges(String customBridges);

    void setBitcoinNodesOptionOrdinal(int bitcoinNodesOption);

    void setReferralId(String referralId);

    void setPhoneKeyAndToken(String phoneKeyAndToken);

    void setUseSoundForMobileNotifications(boolean value);

    void setUseTradeNotifications(boolean value);

    void setUseMarketNotifications(boolean value);

    void setUsePriceNotifications(boolean value);

    List<String> getBridgeAddresses();

    long getWithdrawalTxFeeInBytes();

    void setUseStandbyMode(boolean useStandbyMode);

    void setTakeOfferSelectedPaymentAccountId(String value);

    void setIgnoreDustThreshold(int value);

    void setBuyerSecurityDepositAsPercent(double buyerSecurityDepositAsPercent);

    double getBuyerSecurityDepositAsPercent();

    void setDaoFullNode(boolean value);

    void setRpcUser(String value);

    void setRpcPw(String value);

    void setBlockNotifyPort(int value);

    boolean isDaoFullNode();

    String getRpcUser();

    String getRpcPw();

    int getBlockNotifyPort();
  }

  @Slf4j
  @Data
  @AllArgsConstructor
  public static final class PreferencesPayload {
    private String userLanguage;
    private String userCountry;
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private List<String> fiatCurrencies = new ArrayList<>();
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private List<String> cryptoCurrencies = new ArrayList<>();
    private String blockChainExplorerMainNet;
    private String blockChainExplorerTestNet;
    private String bsqBlockChainExplorer;
    private String backupDirectory;
    private boolean autoSelectArbitrators = true;
    private Map<String, Boolean> dontShowAgainMap = new HashMap<>();
    private boolean tacAccepted;
    private boolean useTorForBitcoinJ = true;
    private boolean showOwnOffersInOfferBook = true;
    private String preferredTradeCurrency;
    private long withdrawalTxFeeInBytes = 100;
    private boolean useCustomWithdrawalTxFee = false;
    private double maxPriceDistanceInPercent = 0.3;
    private String offerBookChartScreenCurrencyCode;
    private String tradeChartsScreenCurrencyCode;
    private String buyScreenCurrencyCode;
    private String sellScreenCurrencyCode;
    private int tradeStatisticsTickUnitIndex = 3;
    private boolean resyncSpvRequested;
    private boolean sortMarketCurrenciesNumerically = true;
    private boolean usePercentageBasedPrice = true;
    private Map<String, String> peerTagMap = new HashMap<>();
    // custom btc nodes
    private String bitcoinNodes = "";
    private List<String> ignoreTradersList = new ArrayList<>();
    private String directoryChooserPath;

    @Deprecated // Superseded by buyerSecurityDepositAsPercent
    private long buyerSecurityDepositAsLong;

    private boolean useAnimations;
    private int cssTheme;
    private String selectedPaymentAccountForCreateOffer;
    private boolean payFeeInBtc = true;
    private List<String> bridgeAddresses;
    private int bridgeOptionOrdinal;
    private int torTransportOrdinal;
    private String customBridges;
    private int bitcoinNodesOptionOrdinal;
    private String referralId;
    private String phoneKeyAndToken;
    private boolean useSoundForMobileNotifications = true;
    private boolean useTradeNotifications = true;
    private boolean useMarketNotifications = true;
    private boolean usePriceNotifications = true;
    private boolean useStandbyMode = false;
    private boolean isDaoFullNode = false;
    private String rpcUser;
    private String rpcPw;
    private String takeOfferSelectedPaymentAccountId;
    private double buyerSecurityDepositAsPercent = 0.0;
    private int ignoreDustThreshold = 600;
    private double buyerSecurityDepositAsPercentForCrypto = 0.0;
    private int blockNotifyPort;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PreferencesPayload() {
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String toProtoMessage() {
      return "";
    }

    public static String fromProto(String s) {
      return s;
    }
  }
}
