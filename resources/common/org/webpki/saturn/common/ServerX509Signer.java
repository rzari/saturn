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

import java.security.cert.X509Certificate;

import java.security.interfaces.RSAPublicKey;

import org.webpki.crypto.AlgorithmPreferences;
import org.webpki.crypto.AsymSignatureAlgorithms;
import org.webpki.crypto.SignatureWrapper;
import org.webpki.crypto.SignerInterface;

import org.webpki.json.JSONX509Signer;

public class ServerX509Signer extends JSONX509Signer {
    
    private static final long serialVersionUID = 1L;

    public ServerX509Signer(final KeyStoreEnumerator key) throws IOException {
        super(new SignerInterface() {
            @Override
            public X509Certificate[] getCertificatePath() throws IOException {
                return key.getCertificatePath();
            }
            @Override
            public byte[] signData(byte[] data, AsymSignatureAlgorithms algorithm) throws IOException {
                try {
                    return new SignatureWrapper(algorithm, key.getPrivateKey()).update(data).sign();
                } catch (GeneralSecurityException e) {
                    throw new IOException (e);
                }
            }
        });
//        setSignatureCertificateAttributes(true);
        setSignatureAlgorithm(key.getPublicKey() instanceof RSAPublicKey ?
                  AsymSignatureAlgorithms.RSA_SHA256 : AsymSignatureAlgorithms.ECDSA_SHA256);
        setAlgorithmPreferences(AlgorithmPreferences.JOSE);
    }
}
