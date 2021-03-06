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

package io.bitsquare.alert;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.storage.data.PubKeyProtectedExpirablePayload;

import java.security.PublicKey;

public final class Alert implements PubKeyProtectedExpirablePayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public static final long TTL = 10 * 24 * 60 * 60 * 1000; // 10 days

    public final String message;
    private String signatureAsBase64;
    private PublicKey storagePublicKey;

    public Alert(String message) {
        this.message = message;
    }

    public void setSigAndStoragePubKey(String signatureAsBase64, PublicKey storagePublicKey) {
        this.signatureAsBase64 = signatureAsBase64;
        this.storagePublicKey = storagePublicKey;
    }

    public String getSignatureAsBase64() {
        return signatureAsBase64;
    }

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public PublicKey getPubKey() {
        return storagePublicKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Alert)) return false;

        Alert that = (Alert) o;

        if (message != null ? !message.equals(that.message) : that.message != null) return false;
        return !(getSignatureAsBase64() != null ? !getSignatureAsBase64().equals(that.getSignatureAsBase64()) : that.getSignatureAsBase64() != null);

    }

    @Override
    public int hashCode() {
        int result = message != null ? message.hashCode() : 0;
        result = 31 * result + (getSignatureAsBase64() != null ? getSignatureAsBase64().hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "AlertMessage{" +
                "message='" + message + '\'' +
                ", signature.hashCode()='" + signatureAsBase64.hashCode() + '\'' +
                '}';
    }
}
