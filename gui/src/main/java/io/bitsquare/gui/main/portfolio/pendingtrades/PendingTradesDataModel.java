/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.portfolio.pendingtrades;

import com.google.inject.Inject;
import io.bitsquare.arbitration.Dispute;
import io.bitsquare.arbitration.DisputeManager;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.model.ActivatableDataModel;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.disputes.DisputesView;
import io.bitsquare.gui.main.portfolio.PortfolioView;
import io.bitsquare.gui.main.portfolio.closedtrades.ClosedTradesView;
import io.bitsquare.gui.popups.Popup;
import io.bitsquare.gui.popups.WalletPasswordPopup;
import io.bitsquare.payment.PaymentAccountContractData;
import io.bitsquare.trade.*;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.bitcoinj.core.BlockChainListener;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.spongycastle.crypto.params.KeyParameter;

import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class PendingTradesDataModel extends ActivatableDataModel {
    private final TradeManager tradeManager;

    private final WalletService walletService;
    private final TradeWalletService tradeWalletService;
    private User user;
    private final KeyRing keyRing;
    private final DisputeManager disputeManager;
    private final Navigation navigation;
    private final WalletPasswordPopup walletPasswordPopup;

    private final ObservableList<PendingTradesListItem> list = FXCollections.observableArrayList();
    private PendingTradesListItem selectedItem;
    private final ListChangeListener<Trade> tradesListChangeListener;
    private boolean isOfferer;

    private final ObjectProperty<Trade> tradeProperty = new SimpleObjectProperty<>();
    private final StringProperty txId = new SimpleStringProperty();
    private Trade trade;
    private Preferences preferences;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PendingTradesDataModel(TradeManager tradeManager, WalletService walletService, TradeWalletService tradeWalletService,
                                  User user, KeyRing keyRing, DisputeManager disputeManager, Preferences preferences,
                                  Navigation navigation, WalletPasswordPopup walletPasswordPopup) {
        this.tradeManager = tradeManager;
        this.walletService = walletService;
        this.tradeWalletService = tradeWalletService;
        this.user = user;
        this.keyRing = keyRing;
        this.disputeManager = disputeManager;
        this.preferences = preferences;
        this.navigation = navigation;
        this.walletPasswordPopup = walletPasswordPopup;

        tradesListChangeListener = change -> onListChanged();
    }

    @Override
    protected void activate() {
        tradeManager.getTrades().addListener(tradesListChangeListener);
        onListChanged();
    }

    @Override
    protected void deactivate() {
        tradeManager.getTrades().removeListener(tradesListChangeListener);
    }

    private void onListChanged() {
        list.clear();
        list.addAll(tradeManager.getTrades().stream().map(PendingTradesListItem::new).collect(Collectors.toList()));

        // we sort by date, earliest first
        list.sort((o1, o2) -> o2.getTrade().getDate().compareTo(o1.getTrade().getDate()));

        // TODO improve selectedItem handling
        // selectedItem does not get set to null if we dont have the view visible
        // So if the item gets removed form the list, and a new item is added we need to check if the old 
        // selectedItem is in the new list, if not we know it is an invalid one
        if (list.size() == 1)
            onSelectTrade(list.get(0));
        else if (list.size() > 1 && (selectedItem == null || !list.contains(selectedItem)))
            onSelectTrade(list.get(0));
        else if (list.size() == 0)
            onSelectTrade(null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onSelectTrade(PendingTradesListItem item) {
        // clean up previous selectedItem
        selectedItem = item;

        if (item == null) {
            trade = null;
            tradeProperty.set(null);
        } else {
            trade = item.getTrade();
            tradeProperty.set(trade);

            isOfferer = tradeManager.isMyOffer(trade.getOffer());

            if (trade.getDepositTx() != null)
                txId.set(trade.getDepositTx().getHashAsString());
        }
    }

    void onFiatPaymentStarted() {
        checkNotNull(trade, "trade must not be null");
        if (trade instanceof BuyerTrade && trade.getDisputeState() == Trade.DisputeState.NONE)
            ((BuyerTrade) trade).onFiatPaymentStarted();
    }

    void onFiatPaymentReceived() {
        checkNotNull(trade, "trade must not be null");
        if (trade instanceof SellerTrade && trade.getDisputeState() == Trade.DisputeState.NONE)
            ((SellerTrade) trade).onFiatPaymentReceived();
    }

    void onWithdrawRequest(String toAddress) {
        checkNotNull(trade, "trade must not be null");
        if (walletService.getWallet().isEncrypted())
            walletPasswordPopup.show().onAesKey(aesKey -> doWithdrawRequest(toAddress, aesKey));
        else
            doWithdrawRequest(toAddress, null);
    }

    private void doWithdrawRequest(String toAddress, KeyParameter aesKey) {
        if (toAddress != null && toAddress.length() > 0) {
            tradeManager.onWithdrawRequest(
                    toAddress,
                    aesKey,
                    trade,
                    () -> UserThread.execute(() -> navigation.navigateTo(MainView.class, PortfolioView.class, ClosedTradesView.class)),
                    (errorMessage, throwable) -> {
                        log.error(errorMessage);
                        new Popup().error("An error occurred:\n" + throwable.getMessage()).show();
                    });
        }
    }

    public void onOpenDispute() {
        doOpenDispute(false);
    }

    public void onOpenSupportTicket() {
        doOpenDispute(true);
    }

    private void doOpenDispute(boolean isSupportTicket) {
        if (trade != null) {
            Transaction depositTx = trade.getDepositTx();
            log.debug("trade.getDepositTx() " + depositTx);
            byte[] depositTxSerialized = null;
            byte[] payoutTxSerialized = null;
            String depositTxHashAsString = null;
            String payoutTxHashAsString = null;
            if (depositTx != null) {
                depositTxSerialized = depositTx.bitcoinSerialize();
                depositTxHashAsString = depositTx.getHashAsString();
            }
            Transaction payoutTx = trade.getPayoutTx();
            if (payoutTx != null) {
                payoutTxSerialized = payoutTx.bitcoinSerialize();
                payoutTxHashAsString = payoutTx.getHashAsString();
            }

            Dispute dispute = new Dispute(disputeManager.getDisputeStorage(),
                    trade.getId(),
                    keyRing.getPubKeyRing().hashCode(), // traderId
                    trade.getOffer().getDirection() == Offer.Direction.BUY ? isOfferer : !isOfferer,
                    isOfferer,
                    keyRing.getPubKeyRing(),
                    trade.getDate(),
                    trade.getContract(),
                    trade.getContractHash(),
                    depositTxSerialized,
                    payoutTxSerialized,
                    depositTxHashAsString,
                    payoutTxHashAsString,
                    trade.getContractAsJson(),
                    trade.getOffererContractSignature(),
                    trade.getTakerContractSignature(),
                    user.getAcceptedArbitratorByAddress(trade.getArbitratorAddress()).getPubKeyRing(),
                    isSupportTicket
            );

            trade.setDisputeState(Trade.DisputeState.DISPUTE_REQUESTED);
            disputeManager.sendOpenNewDisputeMessage(dispute);
            navigation.navigateTo(MainView.class, DisputesView.class);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    ObservableList<PendingTradesListItem> getList() {
        return list;
    }

    boolean isBuyOffer() {
        return trade.getOffer().getDirection() == Offer.Direction.BUY;
    }

    boolean isOfferer() {
        return isOfferer;
    }

    Coin getTotalFees() {
        return FeePolicy.TX_FEE.add(isOfferer() ? FeePolicy.CREATE_OFFER_FEE : FeePolicy.TAKE_OFFER_FEE);
    }

    PendingTradesListItem getSelectedItem() {
        return selectedItem;
    }

    String getCurrencyCode() {
        return trade.getOffer().getCurrencyCode();
    }

    public Offer.Direction getDirection(Offer offer) {
        return isOfferer ? offer.getDirection() : offer.getMirroredDirection();
    }

    Coin getPayoutAmount() {
        return trade.getPayoutAmount();
    }

    Contract getContract() {
        return trade.getContract();
    }

    public Trade getTrade() {
        return trade;
    }

    ReadOnlyObjectProperty<Trade> getTradeProperty() {
        return tradeProperty;
    }

    ReadOnlyStringProperty getTxId() {
        return txId;
    }

    void addBlockChainListener(BlockChainListener blockChainListener) {
        tradeWalletService.addBlockChainListener(blockChainListener);
    }

    void removeBlockChainListener(BlockChainListener blockChainListener) {
        tradeWalletService.removeBlockChainListener(blockChainListener);
    }

    public long getLockTime() {
        return trade.getLockTimeAsBlockHeight();
    }

    public long getCheckPaymentTimeAsBlockHeight() {
        return trade.getCheckPaymentTimeAsBlockHeight();
    }

    public long getOpenDisputeTimeAsBlockHeight() {
        return trade.getOpenDisputeTimeAsBlockHeight();
    }

    public int getBestChainHeight() {
        return tradeWalletService.getBestChainHeight();
    }

    public PaymentAccountContractData getSellersPaymentAccountContractData() {
        return trade.getContract().getSellerPaymentAccountContractData();
    }

    public String getReference() {
        return trade.getOffer().getReferenceText();
    }

    public WalletService getWalletService() {
        return walletService;
    }

    public DisputeManager getDisputeManager() {
        return disputeManager;
    }

    public Preferences getPreferences() {
        return preferences;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

}

