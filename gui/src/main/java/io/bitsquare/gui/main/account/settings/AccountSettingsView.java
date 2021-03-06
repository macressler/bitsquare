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

package io.bitsquare.gui.main.account.settings;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.view.*;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.account.AccountView;
import io.bitsquare.gui.main.account.content.arbitratorselection.ArbitratorSelectionView;
import io.bitsquare.gui.main.account.content.backup.BackupView;
import io.bitsquare.gui.main.account.content.password.PasswordView;
import io.bitsquare.gui.main.account.content.paymentsaccount.PaymentAccountView;
import io.bitsquare.gui.main.account.content.seedwords.SeedWordsView;
import io.bitsquare.gui.util.Colors;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;

import javax.inject.Inject;

@FxmlView
public class AccountSettingsView extends ActivatableViewAndModel {

    private final ViewLoader viewLoader;
    private final Navigation navigation;

    // private MenuItem registration;
    private MenuItem password, seedWords, backup, paymentAccount, arbitratorSelection;
    private Navigation.Listener listener;

    @FXML private VBox leftVBox;
    @FXML private AnchorPane content;

    @Inject
    private AccountSettingsView(CachingViewLoader viewLoader, Navigation navigation) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        listener = viewPath -> {
            if (viewPath.size() != 4 || viewPath.indexOf(AccountSettingsView.class) != 2)
                return;

            loadView(viewPath.tip());
        };

        ToggleGroup toggleGroup = new ToggleGroup();
        password = new MenuItem(navigation, toggleGroup, "Wallet password", PasswordView.class, AwesomeIcon.UNLOCK_ALT);
        seedWords = new MenuItem(navigation, toggleGroup, "Wallet seed", SeedWordsView.class, AwesomeIcon.KEY);
        backup = new MenuItem(navigation, toggleGroup, "Backup", BackupView.class, AwesomeIcon.CLOUD_DOWNLOAD);
        paymentAccount = new MenuItem(navigation, toggleGroup, "Payments account(s)", PaymentAccountView.class, AwesomeIcon.MONEY);
        arbitratorSelection = new MenuItem(navigation, toggleGroup, "Arbitrator selection", ArbitratorSelectionView.class, AwesomeIcon.USER_MD);
        // registration = new MenuItem(navigation, toggleGroup, "Renew your account", RegistrationView.class, AwesomeIcon.BRIEFCASE);

        leftVBox.getChildren().addAll(password, seedWords, backup, paymentAccount, arbitratorSelection);
    }

    @Override
    protected void activate() {
        navigation.addListener(listener);
        ViewPath viewPath = navigation.getCurrentPath();
        if (viewPath.size() == 3 && viewPath.indexOf(AccountSettingsView.class) == 2) {
            navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, PasswordView.class);
        }
        else if (viewPath.size() == 4 && viewPath.indexOf(AccountSettingsView.class) == 2) {
            loadView(viewPath.get(3));
        }
    }

    @Override
    protected void deactivate() {
        navigation.removeListener(listener);
    }

    private void loadView(Class<? extends View> viewClass) {
       /* if (viewClass.equals(PaymentAccountView.class)) {
            PaymentAccountView view = new PaymentAccountView();
            content.getChildren().setAll(view.getRoot());
            paymentAccount.setSelected(true);
        }
        else {*/
        View view = viewLoader.load(viewClass);
        content.getChildren().setAll(view.getRoot());
        if (view instanceof Wizard.Step)
            ((Wizard.Step) view).hideWizardNavigation();


        if (view instanceof PasswordView) password.setSelected(true);
        else if (view instanceof SeedWordsView) seedWords.setSelected(true);
        else if (view instanceof BackupView) backup.setSelected(true);
        else if (view instanceof PaymentAccountView) paymentAccount.setSelected(true);
        else if (view instanceof ArbitratorSelectionView) arbitratorSelection.setSelected(true);
        // else if (view instanceof RegistrationView) registration.setSelected(true);
        //}
    }
}


class MenuItem extends ToggleButton {

    MenuItem(Navigation navigation, ToggleGroup toggleGroup, String title, Class<? extends View> viewClass, AwesomeIcon awesomeIcon) {

        setToggleGroup(toggleGroup);
        setText(title);
        setId("account-settings-item-background-active");
        setPrefHeight(40);
        setPrefWidth(200);
        setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label();
        AwesomeDude.setIcon(icon, awesomeIcon);
        icon.setTextFill(Paint.valueOf("#333"));
        icon.setPadding(new Insets(0, 5, 0, 0));
        icon.setAlignment(Pos.CENTER);
        icon.setMinWidth(25);
        icon.setMaxWidth(25);
        setGraphic(icon);

        setOnAction((event) ->
                navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, viewClass));

        selectedProperty().addListener((ov, oldValue, newValue) -> {
            if (newValue) {
                setId("account-settings-item-background-selected");
                icon.setTextFill(Colors.BLUE);
            }
            else {
                setId("account-settings-item-background-active");
                icon.setTextFill(Paint.valueOf("#333"));
            }
        });

        disableProperty().addListener((ov, oldValue, newValue) -> {
            if (newValue) {
                setId("account-settings-item-background-disabled");
                icon.setTextFill(Paint.valueOf("#ccc"));
            }
            else {
                setId("account-settings-item-background-active");
                icon.setTextFill(Paint.valueOf("#333"));
            }
        });
    }
}

