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

package io.bitsquare.gui.main.home;

import io.bitsquare.BitSquare;
import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.main.arbitrators.registration.ArbitratorRegistrationController;
import io.bitsquare.util.ViewLoader;

import java.io.IOException;

import java.net.URL;

import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

// home is just hosting the arbiters buttons yet, but that's just for dev, not clear yet what will be in home, 
// probably overview, event history, news, charts,... -> low prio
public class HomeController extends CachedViewCB {
    private ArbitratorRegistrationController arbitratorRegistrationController;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("EmptyMethod")
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void activate() {
        super.activate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected Initializable loadView(Navigation.Item navigationItem) {
        // don't use caching here, cause exc. -> need to investigate and is rarely called so no caching is better
        final ViewLoader loader = new ViewLoader(getClass().getResource(navigationItem.getFxmlUrl()), false);
        try {
            final Parent view = loader.load();
            arbitratorRegistrationController = loader.getController();
            arbitratorRegistrationController.setParentController(this);

            final Stage rootStage = BitSquare.getPrimaryStage();
            final Stage stage = new Stage();
            stage.setTitle("Arbitrator");
            stage.setMinWidth(800);
            stage.setMinHeight(400);
            stage.setWidth(800);
            stage.setHeight(600);
            stage.setX(rootStage.getX() + 50);
            stage.setY(rootStage.getY() + 50);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(rootStage);
            Scene scene = new Scene(view, 800, 600);
            stage.setScene(scene);
            stage.show();

            return arbitratorRegistrationController;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    public void onArbitratorRegistration() {
        loadView(Navigation.Item.ARBITRATOR_REGISTRATION);
    }

    @FXML
    public void onArbitratorEdit() {
        loadView(Navigation.Item.ARBITRATOR_REGISTRATION);
        arbitratorRegistrationController.setEditMode(true);
    }


}
