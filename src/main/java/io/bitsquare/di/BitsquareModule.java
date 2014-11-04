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

package io.bitsquare.di;

import io.bitsquare.AbstractBitsquareModule;
import io.bitsquare.btc.BitcoinModule;
import io.bitsquare.crypto.CryptoModule;
import io.bitsquare.gui.GuiModule;
import io.bitsquare.msg.DefaultMessageModule;
import io.bitsquare.msg.MessageModule;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.TradeModule;
import io.bitsquare.user.User;
import io.bitsquare.util.ConfigLoader;

import com.google.inject.Injector;
import com.google.inject.name.Names;

import java.util.Properties;

import javafx.stage.Stage;

import net.tomp2p.connection.Ports;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;
import scala.concurrent.duration.Duration;

public class BitsquareModule extends AbstractBitsquareModule {

    private static final Logger log = LoggerFactory.getLogger(BitsquareModule.class);
    private final Stage primaryStage;
    private final String appName;

    public BitsquareModule(Stage primaryStage, String appName) {
        this(primaryStage, appName, ConfigLoader.loadConfig());
    }

    public BitsquareModule(Stage primaryStage, String appName, Properties properties) {
        super(properties);
        this.primaryStage = primaryStage;
        this.appName = appName;
    }

    @Override
    protected void configure() {
        bind(User.class).asEagerSingleton();
        bind(Persistence.class).asEagerSingleton();
        bind(Settings.class).asEagerSingleton();

        install(messageModule());
        install(bitcoinModule());
        install(cryptoModule());
        install(tradeModule());
        install(guiModule());

        bindConstant().annotatedWith(Names.named("appName")).to(appName);
        bind(ActorSystem.class).toInstance(ActorSystem.create(appName));

        int randomPort = new Ports().tcpPort();
        bindConstant().annotatedWith(Names.named("clientPort")).to(randomPort);
    }

    protected MessageModule messageModule() {
        return new DefaultMessageModule(properties);
    }

    protected BitcoinModule bitcoinModule() {
        return new BitcoinModule(properties);
    }

    protected CryptoModule cryptoModule() {
        return new CryptoModule(properties);
    }

    protected TradeModule tradeModule() {
        return new TradeModule(properties);
    }

    protected GuiModule guiModule() {
        return new GuiModule(properties, primaryStage);
    }

    @Override
    protected void doClose(Injector injector) {
        ActorSystem actorSystem = injector.getInstance(ActorSystem.class);
        actorSystem.shutdown();
        try {
            actorSystem.awaitTermination(Duration.create(5L, "seconds"));
        } catch (Exception ex) {
            log.error("Actor system failed to shut down properly", ex);
        }
    }
}
