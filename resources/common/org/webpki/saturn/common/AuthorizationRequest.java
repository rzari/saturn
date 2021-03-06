/*
 *  Copyright 2015-2018 WebPKI.org (http://webpki.org).
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.webpki.saturn.common;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.GregorianCalendar;
import java.util.Vector;

import org.webpki.crypto.HashAlgorithms;

import org.webpki.json.JSONCryptoHelper;
import org.webpki.json.JSONDecoder;
import org.webpki.json.JSONDecoderCache;
import org.webpki.json.JSONObjectReader;
import org.webpki.json.JSONObjectWriter;
import org.webpki.json.JSONDecryptionDecoder;
import org.webpki.json.JSONOutputFormats;
import org.webpki.json.JSONParser;
import org.webpki.json.JSONSignatureDecoder;

import org.webpki.util.ArrayUtil;
import org.webpki.util.ISODateTime;

public class AuthorizationRequest implements BaseProperties {
    
    public static abstract class PaymentMethodDecoder extends JSONDecoder {

        private static final long serialVersionUID = 1L;

        public abstract boolean match(PaymentMethods payerAccountType) throws IOException;

        public String logLine() throws IOException {
            return getWriter().serializeToString(JSONOutputFormats.NORMALIZED);
        }

        public byte[] getAccountHash() throws IOException {
            JSONObjectReader account = getAccountObject();
            return account == null ? null :
                HashAlgorithms.SHA256.digest(account.serializeToBytes(JSONOutputFormats.CANONICALIZED));
        }

        protected JSONObjectReader getAccountObject() throws IOException {
            return null;
        }
    }

    public static abstract class PaymentMethodEncoder {

        protected abstract JSONObjectWriter writeObject(JSONObjectWriter wr) throws IOException;
        
        public abstract String getContext();
        
        public String getQualifier() {
            return null;  // Optional
        }
        
        public JSONObjectWriter writeObject() throws IOException {
            return new JSONObjectWriter()
                .setString(JSONDecoderCache.CONTEXT_JSON, getContext())
                .setDynamic((wr) -> {
                    if (getQualifier() != null) {
                        wr.setString(JSONDecoderCache.QUALIFIER_JSON, getQualifier());
                    }
                    return writeObject(wr);
                });
        }
    }

    public AuthorizationRequest(JSONObjectReader rd) throws IOException {
        root = Messages.AUTHORIZATION_REQUEST.parseBaseMessage(rd);
        testMode = rd.getBooleanConditional(TEST_MODE_JSON);
        recepientUrl = rd.getString(RECEPIENT_URL_JSON);
        authorityUrl = rd.getString(AUTHORITY_URL_JSON);
        paymentMethod = PaymentMethods.fromTypeUri(rd.getString(PAYMENT_METHOD_JSON));
        paymentRequest = new PaymentRequest(rd.getObject(PAYMENT_REQUEST_JSON));
        encryptedAuthorizationData = 
                rd.getObject(ENCRYPTED_AUTHORIZATION_JSON)
                    .getEncryptionObject(new JSONCryptoHelper.Options()).require(true);
        undecodedPaymentMethodSpecific = rd.getObject(PAYMENT_METHOD_SPECIFIC_JSON);
        rd.scanAway(PAYMENT_METHOD_SPECIFIC_JSON);  // Read all to not throw on checkForUnread()
        referenceId = rd.getString(REFERENCE_ID_JSON);
        clientIpAddress = rd.getString(CLIENT_IP_ADDRESS_JSON);
        timeStamp = rd.getDateTime(TIME_STAMP_JSON, ISODateTime.COMPLETE);
        software = new Software(rd);
        signatureDecoder = rd.getSignature(new JSONCryptoHelper.Options());
        rd.checkForUnread();
    }

    Software software;

    JSONDecryptionDecoder encryptedAuthorizationData;

    JSONObjectReader root;
    
    JSONObjectReader undecodedPaymentMethodSpecific;

    boolean testMode;
    public boolean getTestMode() {
        return testMode;
    }

    PaymentMethods paymentMethod;
    public PaymentMethods getPaymentMethod() {
        return paymentMethod;
    }

    JSONSignatureDecoder signatureDecoder;
    public JSONSignatureDecoder getSignatureDecoder() {
        return signatureDecoder;
    }

    public PaymentMethodDecoder getPaymentMethodSpecific(JSONDecoderCache knownPaymentMethods) throws IOException {
        PaymentMethodDecoder paymentMethodSpecific =
            (PaymentMethodDecoder) knownPaymentMethods.parse(undecodedPaymentMethodSpecific.clone()); // Clone => Fresh read
        if (!paymentMethodSpecific.match(paymentMethod)) {
            throw new IOException("\"" + PAYMENT_METHOD_SPECIFIC_JSON + 
                                  "\" data incompatible with \"" + PAYMENT_METHOD_JSON + "\"");
        }
        return paymentMethodSpecific;
    }

    GregorianCalendar timeStamp;
    public GregorianCalendar getTimeStamp() {
        return timeStamp;
    }

    String recepientUrl;
    public String getRecepientUrl() {
        return recepientUrl;
    }

    String authorityUrl;
    public String getAuthorityUrl() {
        return authorityUrl;
    }

    String referenceId;
    public String getReferenceId() {
        return referenceId;
    }

    String clientIpAddress;
    public String getClientIpAddress() {
        return clientIpAddress;
    }

    PaymentRequest paymentRequest;
    public PaymentRequest getPaymentRequest() {
        return paymentRequest;
    }

    public static JSONObjectWriter encode(Boolean testMode,
                                          String recepientUrl,
                                          String authorityUrl,
                                          PaymentMethods paymentMethod,
                                          JSONObjectReader encryptedAuthorizationData,
                                          String clientIpAddress,
                                          PaymentRequest paymentRequest,
                                          PaymentMethodEncoder paymentMethodSpecific,
                                          String referenceId,
                                          ServerAsymKeySigner signer) throws IOException {
        return Messages.AUTHORIZATION_REQUEST.createBaseMessage()
            .setDynamic((wr) -> testMode == null ? wr : wr.setBoolean(TEST_MODE_JSON, testMode))
            .setString(RECEPIENT_URL_JSON, recepientUrl)
            .setString(AUTHORITY_URL_JSON, authorityUrl)
            .setString(PAYMENT_METHOD_JSON, paymentMethod.getPaymentMethodUri())
            .setObject(PAYMENT_REQUEST_JSON, paymentRequest.root)
            .setObject(ENCRYPTED_AUTHORIZATION_JSON, encryptedAuthorizationData)
            .setObject(PAYMENT_METHOD_SPECIFIC_JSON, paymentMethodSpecific.writeObject())
            .setString(REFERENCE_ID_JSON, referenceId)
            .setString(CLIENT_IP_ADDRESS_JSON, clientIpAddress)
            .setDateTime(TIME_STAMP_JSON, new GregorianCalendar(), ISODateTime.UTC_NO_SUBSECONDS)
            .setObject(SOFTWARE_JSON, Software.encode(PaymentRequest.SOFTWARE_NAME, PaymentRequest.SOFTWARE_VERSION))
            .setSignature(signer);
    }

    public AuthorizationData getDecryptedAuthorizationData(
            Vector<JSONDecryptionDecoder.DecryptionKeyHolder> decryptionKeys)
    throws IOException, GeneralSecurityException {
        AuthorizationData authorizationData =
            new AuthorizationData(JSONParser.parse(encryptedAuthorizationData.getDecryptedData(decryptionKeys)));
        if (!ArrayUtil.compare(authorizationData.requestHash, paymentRequest.getRequestHash())) {
            throw new IOException("Non-matching \"" + REQUEST_HASH_JSON + "\" value");
        }
        if (!authorizationData.paymentMethod.equals(paymentMethod.paymentMethodUri)) {
            throw new IOException("Non-matching \"" + PAYMENT_METHOD_JSON + "\"");
        }
        return authorizationData;
    }
}
