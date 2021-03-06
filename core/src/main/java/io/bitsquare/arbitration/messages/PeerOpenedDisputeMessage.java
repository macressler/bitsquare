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

package io.bitsquare.arbitration.messages;

import io.bitsquare.app.Version;
import io.bitsquare.arbitration.Dispute;
import io.bitsquare.p2p.Address;

public final class PeerOpenedDisputeMessage extends DisputeMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;
    public final Dispute dispute;
    private final Address myAddress;

    public PeerOpenedDisputeMessage(Dispute dispute, Address myAddress) {
        this.dispute = dispute;
        this.myAddress = myAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PeerOpenedDisputeMessage)) return false;

        PeerOpenedDisputeMessage that = (PeerOpenedDisputeMessage) o;

        if (dispute != null ? !dispute.equals(that.dispute) : that.dispute != null) return false;
        return !(myAddress != null ? !myAddress.equals(that.myAddress) : that.myAddress != null);

    }

    @Override
    public int hashCode() {
        int result = dispute != null ? dispute.hashCode() : 0;
        result = 31 * result + (myAddress != null ? myAddress.hashCode() : 0);
        return result;
    }

    @Override
    public Address getSenderAddress() {
        return myAddress;
    }
}
