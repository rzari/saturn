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

// Credential initiator to the Payment Agent (a.k.a. Wallet) application

package org.webpki.saturn.resources;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.math.BigDecimal;

import java.security.KeyPair;
import java.security.PublicKey;

import java.security.cert.X509Certificate;

import java.security.interfaces.RSAKey;

import java.util.EnumSet;

import org.webpki.crypto.AlgorithmPreferences;
import org.webpki.crypto.AsymSignatureAlgorithms;
import org.webpki.crypto.CertificateUtil;
import org.webpki.crypto.KeyAlgorithms;

import org.webpki.json.DataEncryptionAlgorithms;
import org.webpki.json.JSONArrayReader;
import org.webpki.json.JSONArrayWriter;
import org.webpki.json.JSONObjectReader;
import org.webpki.json.JSONObjectWriter;
import org.webpki.json.JSONOutputFormats;
import org.webpki.json.JSONParser;
import org.webpki.json.KeyEncryptionAlgorithms;

import org.webpki.keygen2.KeyGen2URIs;

import org.webpki.saturn.common.BaseProperties;
import org.webpki.saturn.common.CardDataEncoder;
import org.webpki.saturn.common.PaymentMethods;
import org.webpki.saturn.common.TemporaryCardDBDecoder;

import org.webpki.sks.AppUsage;
import org.webpki.sks.BiometricProtection;
import org.webpki.sks.DeleteProtection;
import org.webpki.sks.EnumeratedKey;
import org.webpki.sks.ExportProtection;
import org.webpki.sks.Grouping;
import org.webpki.sks.InputMethod;
import org.webpki.sks.PassphraseFormat;
import org.webpki.sks.PatternRestriction;
import org.webpki.sks.SecureKeyStore;
import org.webpki.sks.Device;
import org.webpki.sks.GenKey;
import org.webpki.sks.KeySpecifier;
import org.webpki.sks.PINPol;
import org.webpki.sks.ProvSess;
import org.webpki.sks.SKSReferenceImplementation;

import org.webpki.util.ArrayUtil;

public class InitWallet {

    public static void main(String[] args) throws Exception {
        if (args.length != 12) {
            System.out.println("\nUsage: " +
                               InitWallet.class.getCanonicalName() +
                               "sksFile accountDbFile clientKeyCore kg2Pin accountType accountId balance" +
                               " authorityUrl keyEncryptionKey imageFile dataEncrytionAlgorithm keyEncrytionAlgorithm");
            System.exit(-3);
        }
        String sksFile = args[0];
        String accountDbFile = args[1];
        String clientKeyCore = args[2];
        String kg2Pin = args[3];
        PaymentMethods paymentMethod = PaymentMethods.valueOf(args[4]);
        String accountId = args[5];
        String balance = args[6];
        String authorityUrl = args[7];
        PublicKey encryptionKey = CertificateUtil.getCertificateFromBlob(ArrayUtil.readFile(args[8])).getPublicKey();
        String imageFile = args[9];
        DataEncryptionAlgorithms dataEncryptionAlgorithm = DataEncryptionAlgorithms.getAlgorithmFromId(args[10]);
        KeyEncryptionAlgorithms keyEncryptionAlgorithm = KeyEncryptionAlgorithms.getAlgorithmFromId(args[11]);
  
        // Read importedKey/certificate to be imported
        JSONObjectReader privateKeyJWK = JSONParser.parse(ArrayUtil.readFile(clientKeyCore + ".jwk"));
        JSONArrayReader certPathJSON = JSONParser.parse(ArrayUtil.readFile(clientKeyCore + ".certpath")).getJSONArrayReader();
        X509Certificate[] certPath = certPathJSON.getCertificatePath();
        KeyPair keyPair = privateKeyJWK.getKeyPair();
        boolean rsa_flag = keyPair.getPublic() instanceof RSAKey;
        String[] endorsed_algs = rsa_flag ?
                new String[] {AsymSignatureAlgorithms.RSA_SHA256.getAlgorithmId(AlgorithmPreferences.SKS)} 
                                          : 
                new String[] {AsymSignatureAlgorithms.ECDSA_SHA256.getAlgorithmId(AlgorithmPreferences.SKS)};

        // Setup keystore (SKS)
        SKSReferenceImplementation sks = null;
        try {
            sks = (SKSReferenceImplementation) new ObjectInputStream(new FileInputStream(sksFile)).readObject();
            System.out.println("SKS found, restoring it");
        } catch (Exception e) {
            sks = new SKSReferenceImplementation();
            System.out.println("SKS not found, creating it");
        }
        Device device = new Device(sks);

        // Check for duplicates
        EnumeratedKey ek = new EnumeratedKey();
        while ((ek = sks.enumerateKeys(ek.getKeyHandle())) != null) {
            if (sks.getKeyAttributes(ek.getKeyHandle()).getCertificatePath()[0].equals(certPath[0])) {
                throw new IOException("Duplicate entry - importedKey #" + ek.getKeyHandle());
            }
        }

        // Start process by creating a session
        ProvSess sess = new ProvSess(device, 0);
        sess.setInputMethod(InputMethod.ANY);
        PINPol pin_policy = sess.createPINPolicy("PIN", 
                                                 PassphraseFormat.STRING,
                                                 EnumSet.noneOf(PatternRestriction.class),
                                                 Grouping.NONE,
                                                 1 /* min_length */,
                                                 50 /* max_length */,
                                                 (short) 3 /* retry_limit */,
                                                 null /* puk_policy */);

        GenKey surrogateKey = sess.createKey("Key",
                                             SecureKeyStore.ALGORITHM_KEY_ATTEST_1,
                                             null /* server_seed */,
                                             pin_policy, 
                                             kg2Pin.equals("@") ? "1234" : kg2Pin /* PIN value */,
                                             BiometricProtection.NONE /* biometric_protection */,
                                             ExportProtection.NON_EXPORTABLE /* export_policy */,
                                             DeleteProtection.NONE /* delete_policy */,
                                             false /* enable_pin_caching */,
                                             AppUsage.SIGNATURE,
                                             "" /* friendly_name */, 
                                             new KeySpecifier(KeyAlgorithms.NIST_P_256), endorsed_algs);

        surrogateKey.setCertificatePath(certPath);
        surrogateKey.setPrivateKey(new KeyPair(keyPair.getPublic(), keyPair.getPrivate()));
        JSONObjectWriter ow = null;
        boolean cardNumberFormatting = true;
        if (accountId.startsWith("!")) {
            cardNumberFormatting = false;
            accountId = accountId.substring(1);
        }
        ow = CardDataEncoder.encode(paymentMethod.getPaymentMethodUri(), 
                                    accountId, 
                                    authorityUrl,
                                    rsa_flag ?
                                         AsymSignatureAlgorithms.RSA_SHA256
                                                           :
                                         AsymSignatureAlgorithms.ECDSA_SHA256,
                                    dataEncryptionAlgorithm, 
                                    keyEncryptionAlgorithm, 
                                    encryptionKey, 
                                    null,
                                    null,
                                    new BigDecimal(balance));
        surrogateKey.addExtension(BaseProperties.SATURN_WEB_PAY_CONTEXT_URI,
                                  SecureKeyStore.SUB_TYPE_EXTENSION,
                                  "",
                                  ow.serializeToBytes(JSONOutputFormats.NORMALIZED));
        surrogateKey.addExtension(KeyGen2URIs.LOGOTYPES.CARD,
                                  SecureKeyStore.SUB_TYPE_LOGOTYPE,
                                  "image/png",
                                  ArrayUtil.readFile(imageFile));
        sess.closeSession();
        // Serialize the updated SKS
        ObjectOutputStream oos = new ObjectOutputStream (new FileOutputStream(sksFile));
        oos.writeObject (sks);
        oos.close ();
        // Add the account to the account DB
        imageFile = imageFile.substring(imageFile.lastIndexOf(File.separatorChar));
        JSONArrayWriter accountDb =
                new JSONArrayWriter(JSONParser.parse(ArrayUtil.readFile(accountDbFile)).getJSONArrayReader());
        accountDb.setObject()
            .setObject(TemporaryCardDBDecoder.CORE_CARD_DATA_JSON, ow)
            .setString(TemporaryCardDBDecoder.LOGOTYPE_NAME_JSON, 
                    imageFile.substring(1, imageFile.lastIndexOf('.')) + ".svg")
            .setBoolean(TemporaryCardDBDecoder.FORMAT_ACCOUNT_AS_CARD_JSON, cardNumberFormatting)
            .setString(TemporaryCardDBDecoder.CARD_HOLDER_JSON, "Luke Skywalker")
            .setString(TemporaryCardDBDecoder.CARD_PIN_JSON, kg2Pin)
            .setObject(TemporaryCardDBDecoder.CARD_PRIVATE_KEY_JSON, privateKeyJWK)
            .setArray(TemporaryCardDBDecoder.CARD_DUMMY_CERTIFICATE_JSON, new JSONArrayWriter(certPathJSON));

        ArrayUtil.writeFile(accountDbFile, accountDb.serializeToBytes(JSONOutputFormats.PRETTY_PRINT));

        // Report
        System.out.println("Imported Subject: " +
                certPath[0].getSubjectX500Principal().getName() +
                "\nID=#" + surrogateKey.keyHandle + ", " + (rsa_flag ? "RSA" : "EC") +
                (ow == null ? ", Not a card" : ", Card=\n" + ow));
    }
}
