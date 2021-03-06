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

package io.bitsquare.trade.protocol.trade;


import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.messaging.MailboxMessage;
import io.bitsquare.trade.SellerAsOffererTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.messages.*;
import io.bitsquare.trade.protocol.trade.tasks.offerer.*;
import io.bitsquare.trade.protocol.trade.tasks.seller.CreateAndSignDepositTxAsSeller;
import io.bitsquare.trade.protocol.trade.tasks.seller.ProcessFiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.tasks.seller.ProcessPayoutTxFinalizedMessage;
import io.bitsquare.trade.protocol.trade.tasks.seller.SendFinalizePayoutTxRequest;
import io.bitsquare.trade.protocol.trade.tasks.shared.CommitPayoutTx;
import io.bitsquare.trade.protocol.trade.tasks.shared.InitWaitPeriodForOpenDispute;
import io.bitsquare.trade.protocol.trade.tasks.shared.SetupPayoutTxLockTimeReachedListener;
import io.bitsquare.trade.protocol.trade.tasks.shared.SignPayoutTx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static io.bitsquare.util.Validator.checkTradeId;

public class SellerAsOffererProtocol extends TradeProtocol implements SellerProtocol, OffererProtocol {
    private static final Logger log = LoggerFactory.getLogger(SellerAsOffererProtocol.class);

    private final SellerAsOffererTrade sellerAsOffererTrade;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerAsOffererProtocol(SellerAsOffererTrade trade) {
        super(trade);

        this.sellerAsOffererTrade = trade;

        // If we are after the time lock state we need to setup the listener again
        //TODO not sure if that is not called already from the checkPayoutTxTimeLock at tradeProtocol
        Trade.State tradeState = trade.getState();
        if (tradeState.getPhase() == Trade.Phase.PAYOUT_PAID) {
            TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                    () -> {
                        handleTaskRunnerSuccess("SetupPayoutTxLockTimeReachedListener");
                        processModel.onComplete();
                    },
                    this::handleTaskRunnerFault);

            taskRunner.addTasks(SetupPayoutTxLockTimeReachedListener.class);
            taskRunner.run();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Mailbox
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void doApplyMailboxMessage(Message message, Trade trade) {
        this.trade = trade;

        Address peerAddress = ((MailboxMessage) message).getSenderAddress();
        if (message instanceof PayoutTxFinalizedMessage) {
            handle((PayoutTxFinalizedMessage) message, peerAddress);
        } else {
            if (message instanceof FiatTransferStartedMessage) {
                handle((FiatTransferStartedMessage) message, peerAddress);
            } else if (message instanceof DepositTxPublishedMessage) {
                handle((DepositTxPublishedMessage) message, peerAddress);
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Start trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleTakeOfferRequest(TradeMessage message, Address sender) {
        checkTradeId(processModel.getId(), message);
        checkArgument(message instanceof PayDepositRequest);
        processModel.setTradeMessage(message);
        processModel.setTempTradingPeerAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsOffererTrade,
                () -> handleTaskRunnerSuccess("handleTakeOfferRequest"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                ProcessPayDepositRequest.class,
                VerifyArbitrationSelection.class,
                VerifyTakerAccount.class,
                LoadTakeOfferFeeTx.class,
                InitWaitPeriodForOpenDispute.class,
                CreateAndSignContract.class,
                CreateAndSignDepositTxAsSeller.class,
                SetupDepositBalanceListener.class,
                SendPublishDepositTxRequest.class
        );
        startTimeout();
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling 
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(DepositTxPublishedMessage tradeMessage, Address sender) {
        stopTimeout();
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsOffererTrade,
                () -> handleTaskRunnerSuccess("DepositTxPublishedMessage"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                ProcessDepositTxPublishedMessage.class,
                AddDepositTxToWallet.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // After peer has started Fiat tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(FiatTransferStartedMessage tradeMessage, Address sender) {
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsOffererTrade,
                () -> handleTaskRunnerSuccess("FiatTransferStartedMessage"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(ProcessFiatTransferStartedMessage.class);
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer received" button, so we release the funds for pay out
    @Override
    public void onFiatPaymentReceived() {
        sellerAsOffererTrade.setState(Trade.State.FIAT_PAYMENT_RECEIPT);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsOffererTrade,
                () -> handleTaskRunnerSuccess("onFiatPaymentReceived"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                VerifyTakeOfferFeePayment.class,
                SignPayoutTx.class,
                SendFinalizePayoutTxRequest.class
        );
        taskRunner.run();
    }

    private void handle(PayoutTxFinalizedMessage tradeMessage, Address sender) {
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsOffererTrade,
                () -> {
                    handleTaskRunnerSuccess("PayoutTxFinalizedMessage");
                    processModel.onComplete();
                },
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                ProcessPayoutTxFinalizedMessage.class,
                CommitPayoutTx.class,
                SetupPayoutTxLockTimeReachedListener.class
        );
        taskRunner.run();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Massage dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void doHandleDecryptedMessage(TradeMessage tradeMessage, Address sender) {
        if (tradeMessage instanceof DepositTxPublishedMessage) {
            handle((DepositTxPublishedMessage) tradeMessage, sender);
        } else if (tradeMessage instanceof FiatTransferStartedMessage) {
            handle((FiatTransferStartedMessage) tradeMessage, sender);
        } else if (tradeMessage instanceof PayoutTxFinalizedMessage) {
            handle((PayoutTxFinalizedMessage) tradeMessage, sender);
        } else {
            log.error("Incoming tradeMessage not supported. " + tradeMessage);
        }
    }
}
