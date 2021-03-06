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
import io.bitsquare.trade.BuyerAsOffererTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.messages.DepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.messages.FinalizePayoutTxRequest;
import io.bitsquare.trade.protocol.trade.messages.PayDepositRequest;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.trade.protocol.trade.tasks.buyer.*;
import io.bitsquare.trade.protocol.trade.tasks.offerer.*;
import io.bitsquare.trade.protocol.trade.tasks.shared.CommitPayoutTx;
import io.bitsquare.trade.protocol.trade.tasks.shared.InitWaitPeriodForOpenDispute;
import io.bitsquare.trade.protocol.trade.tasks.shared.SetupPayoutTxLockTimeReachedListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static io.bitsquare.util.Validator.checkTradeId;

public class BuyerAsOffererProtocol extends TradeProtocol implements BuyerProtocol, OffererProtocol {
    private static final Logger log = LoggerFactory.getLogger(BuyerAsOffererProtocol.class);

    private final BuyerAsOffererTrade buyerAsOffererTrade;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerAsOffererProtocol(BuyerAsOffererTrade trade) {
        super(trade);

        this.buyerAsOffererTrade = trade;

        // If we are after the time lock state we need to setup the listener again
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

        if (message instanceof MailboxMessage) {
            MailboxMessage mailboxMessage = (MailboxMessage) message;
            Address peerAddress = mailboxMessage.getSenderAddress();
            if (message instanceof FinalizePayoutTxRequest) {
                handle((FinalizePayoutTxRequest) message, peerAddress);
            } else if (message instanceof DepositTxPublishedMessage) {
                handle((DepositTxPublishedMessage) message, peerAddress);
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Start trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleTakeOfferRequest(TradeMessage message, Address peerAddress) {
        checkTradeId(processModel.getId(), message);
        checkArgument(message instanceof PayDepositRequest);
        processModel.setTradeMessage(message);
        processModel.setTempTradingPeerAddress(peerAddress);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsOffererTrade,
                () -> handleTaskRunnerSuccess("handleTakeOfferRequest"),
                this::handleTaskRunnerFault);
        taskRunner.addTasks(
                ProcessPayDepositRequest.class,
                VerifyArbitrationSelection.class,
                VerifyTakerAccount.class,
                LoadTakeOfferFeeTx.class,
                CreateAndSignContract.class,
                CreateAndSignDepositTxAsBuyer.class,
                InitWaitPeriodForOpenDispute.class,
                SetupDepositBalanceListener.class,
                SendPublishDepositTxRequest.class
        );
        startTimeout();
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(DepositTxPublishedMessage tradeMessage, Address peerAddress) {
        stopTimeout();
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerAddress(peerAddress);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsOffererTrade,
                () -> handleTaskRunnerSuccess("handle DepositTxPublishedMessage"),
                this::handleTaskRunnerFault);
        taskRunner.addTasks(
                ProcessDepositTxPublishedMessage.class,
                AddDepositTxToWallet.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer started" button
    @Override
    public void onFiatPaymentStarted() {
        buyerAsOffererTrade.setState(Trade.State.FIAT_PAYMENT_STARTED);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsOffererTrade,
                () -> handleTaskRunnerSuccess("onFiatPaymentStarted"),
                this::handleTaskRunnerFault);
        taskRunner.addTasks(
                VerifyTakeOfferFeePayment.class,
                SendFiatTransferStartedMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(FinalizePayoutTxRequest tradeMessage, Address peerAddress) {
        log.debug("handle RequestFinalizePayoutTxMessage called");
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerAddress(peerAddress);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsOffererTrade,
                () -> {
                    handleTaskRunnerSuccess("handle FinalizePayoutTxRequest");
                    processModel.onComplete();
                },
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                ProcessFinalizePayoutTxRequest.class,
                SignAndFinalizePayoutTx.class,
                CommitPayoutTx.class,
                SendPayoutTxFinalizedMessage.class,
                SetupPayoutTxLockTimeReachedListener.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Massage dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void doHandleDecryptedMessage(TradeMessage tradeMessage, Address peerAddress) {
        if (tradeMessage instanceof DepositTxPublishedMessage) {
            handle((DepositTxPublishedMessage) tradeMessage, peerAddress);
        } else if (tradeMessage instanceof FinalizePayoutTxRequest) {
            handle((FinalizePayoutTxRequest) tradeMessage, peerAddress);
        } else {
            log.error("Incoming decrypted tradeMessage not supported. " + tradeMessage);
        }
    }
}
