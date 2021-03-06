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

package io.bitsquare.gui.components.paymentmethods;

import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.gui.util.validation.AliPayValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.payment.AliPayAccount;
import io.bitsquare.payment.AliPayAccountContractData;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentAccountContractData;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.FormBuilder.*;

public class AliPayForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(AliPayForm.class);

    private final AliPayAccount aliPayAccount;
    private final AliPayValidator aliPayValidator;
    private InputTextField accountNrInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountContractData paymentAccountContractData) {
        addLabelTextField(gridPane, ++gridRow, "Payment method:", BSResources.get(paymentAccountContractData.getPaymentMethodName()));
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Account nr.:", ((AliPayAccountContractData) paymentAccountContractData).getAccountNr());
        addAllowedPeriod(gridPane, ++gridRow, paymentAccountContractData);
        return gridRow;
    }

    public AliPayForm(PaymentAccount paymentAccount, AliPayValidator aliPayValidator, InputValidator inputValidator, GridPane gridPane, int gridRow) {
        super(paymentAccount, inputValidator, gridPane, gridRow);
        this.aliPayAccount = (AliPayAccount) paymentAccount;
        this.aliPayValidator = aliPayValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        accountNrInputTextField = addLabelInputTextField(gridPane, ++gridRow, "Account nr.:").second;
        accountNrInputTextField.setValidator(aliPayValidator);
        accountNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            aliPayAccount.setAccountNr(newValue);
            updateFromInputs();
        });

        addLabelTextField(gridPane, ++gridRow, "Currency:", aliPayAccount.getSingleTradeCurrency().getCodeAndName());
        addAllowedPeriod();
        addAccountNameTextFieldWithAutoFillCheckBox();
    }

    @Override
    protected void autoFillNameTextField() {
        if (autoFillCheckBox != null && autoFillCheckBox.isSelected()) {
            String accountNr = accountNrInputTextField.getText();
            accountNr = accountNr.substring(0, Math.min(5, accountNr.length())) + "...";
            String method = BSResources.get(paymentAccount.getPaymentMethod().getId());
            accountNameTextField.setText(method.concat(", ").concat(accountNr));
        }
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addLabelTextField(gridPane, gridRow, "Account name:", aliPayAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, "Payment method:", BSResources.get(aliPayAccount.getPaymentMethod().getId()));
        addLabelTextField(gridPane, ++gridRow, "Account nr.:", aliPayAccount.getAccountNr());
        addLabelTextField(gridPane, ++gridRow, "Currency:", aliPayAccount.getSingleTradeCurrency().getCodeAndName());
        addAllowedPeriod();
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && aliPayValidator.validate(aliPayAccount.getAccountNr()).isValid
                && aliPayAccount.getTradeCurrencies().size() > 0);
    }

}
