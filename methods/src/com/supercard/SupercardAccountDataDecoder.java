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
package com.supercard;

import java.io.IOException;

import java.util.GregorianCalendar;

import org.webpki.json.JSONObjectReader;

import org.webpki.saturn.common.BaseProperties;
import org.webpki.saturn.common.AuthorizationResponse;

import org.webpki.util.ISODateTime;

public final class SupercardAccountDataDecoder extends AuthorizationResponse.AccountDataDecoder {

    private static final long serialVersionUID = 1L;

    String cardNumber;                   // PAN
    public String getCardNumber() {
        return cardNumber;
    }

    String cardHolder;                   // Name
    public String getCardHolder() {
        return cardHolder;
    }

    GregorianCalendar expirationDate;    // Card expiration date
    public GregorianCalendar getExpirationDate() {
        return expirationDate;
    }

    String securityCode;                 // CCV or similar
    public String getSecurityCode() {
        return securityCode;
    }

    @Override
    protected void readJSONData(JSONObjectReader rd) throws IOException {
        cardNumber = rd.getString(SupercardAccountDataEncoder.CARD_NUMBER_JSON);
        cardHolder = rd.getString(SupercardAccountDataEncoder.CARD_HOLDER_JSON);
        expirationDate = rd.getDateTime(BaseProperties.EXPIRES_JSON, ISODateTime.COMPLETE);
        securityCode = rd.getString(SupercardAccountDataEncoder.SECURITY_CODE_JSON);
    }

    @Override
    public String getContext() {
        return SupercardAccountDataEncoder.ACCOUNT_DATA;
    }
}
