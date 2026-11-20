package com.termux.app.ssh;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Calendar;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;

/**
 * Production {@link SshSecretCipher} backed by {@code AndroidKeyStore}:
 * <ul>
 *   <li>API 23+ (Android 6.0+): generates and uses a hardware/TEE-backed
 *       {@code AES/GCM/NoPadding} 256-bit key directly in {@code AndroidKeyStore}
 *       with Keystore-enforced random IV per encryption.</li>
 *   <li>API 21–22 (Android 5.x): generates an {@code AndroidKeyStore} RSA-2048 key pair
 *       via {@link KeyPairGeneratorSpec} to wrap a randomly generated 256-bit AES key
 *       stored in app-private {@code 0600} storage, then runs {@code AES/GCM/NoPadding}.</li>
 * </ul>
 */
public final class AndroidKeystoreSshSecretCipher implements SshSecretCipher {

    public static final String DEFAULT_KEY_ALIAS = "com.termux.ssh.credentials.v1";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String RSA_WRAP_TRANSFORMATION = "RSA/ECB/PKCS1Padding";
    private static final String LEGACY_WRAPPED_KEY_FILENAME = ".master_key.bin";

    private final Context appContext;
    private final String keyAlias;
    private final File wrappedKeyFile;
    private final Object lock = new Object();

    public AndroidKeystoreSshSecretCipher(@NonNull Context context) {
        this(
            context,
            DEFAULT_KEY_ALIAS,
            new File(getDefaultStorageDir(context), LEGACY_WRAPPED_KEY_FILENAME)
        );
    }

    public AndroidKeystoreSshSecretCipher(
            @NonNull Context context,
            @NonNull String keyAlias,
            @NonNull File wrappedKeyFile) {
        Context app = context.getApplicationContext();
        this.appContext = app != null ? app : context;
        this.keyAlias = keyAlias;
        this.wrappedKeyFile = wrappedKeyFile;
    }

    @NonNull
    static File getDefaultStorageDir(@NonNull Context context) {
        File noBackup = context.getNoBackupFilesDir();
        if (noBackup != null) {
            return new File(noBackup, "ssh");
        }
        return new File(context.getFilesDir(), "apps/com.termux/ssh");
    }

    @NonNull
    @Override
    public byte[] encrypt(@NonNull byte[] plaintext, @Nullable byte[] associatedData)
        throws GeneralSecurityException {
        synchronized (lock) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return Api23KeystoreHelper.encrypt(keyAlias, plaintext, associatedData);
            }
            return getOrCreateLegacySoftwareCipher().encrypt(plaintext, associatedData);
        }
    }

    @NonNull
    @Override
    public byte[] decrypt(@NonNull byte[] ciphertextPayload, @Nullable byte[] associatedData)
        throws GeneralSecurityException {
        synchronized (lock) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return Api23KeystoreHelper.decrypt(keyAlias, ciphertextPayload, associatedData);
            }
            return getOrCreateLegacySoftwareCipher().decrypt(ciphertextPayload, associatedData);
        }
    }

    @NonNull
    private AesGcmSshSecretCipher getOrCreateLegacySoftwareCipher() throws GeneralSecurityException {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            String rsaAlias = keyAlias + ".rsa_wrap";
            if (!keyStore.containsAlias(rsaAlias)) {
                generateLegacyRsaKeyPair(appContext, rsaAlias);
            }
            KeyStore.Entry entry = keyStore.getEntry(rsaAlias, null);
            if (!(entry instanceof KeyStore.PrivateKeyEntry)) {
                throw new GeneralSecurityException("Missing RSA wrapping key in AndroidKeyStore");
            }
            KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) entry;

            byte[] rawAesKey;
            if (wrappedKeyFile.isFile()) {
                byte[] wrapped = SshFsUtils.readBytes(wrappedKeyFile);
                rawAesKey = unwrapKey(privateKeyEntry.getPrivateKey(), wrapped);
            } else {
                KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
                keyGenerator.init(256);
                rawAesKey = keyGenerator.generateKey().getEncoded();
                byte[] wrapped = wrapKey(privateKeyEntry.getCertificate().getPublicKey(), rawAesKey);
                SshFsUtils.writeBytesAtomic0600(wrappedKeyFile, wrapped);
            }
            return new AesGcmSshSecretCipher(new SecretKeySpec(rawAesKey, "AES"));
        } catch (IOException e) {
            throw new GeneralSecurityException("Failed to access wrapped legacy SSH master key", e);
        }
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("TrulyRandom")
    private static void generateLegacyRsaKeyPair(
            @NonNull Context context,
            @NonNull String rsaAlias) throws GeneralSecurityException {
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        end.add(Calendar.YEAR, 30);
        KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(context)
            .setAlias(rsaAlias)
            .setSubject(new X500Principal("CN=" + rsaAlias))
            .setSerialNumber(BigInteger.ONE)
            .setKeySize(2048)
            .setStartDate(start.getTime())
            .setEndDate(end.getTime())
            .build();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", ANDROID_KEYSTORE);
        generator.initialize(spec);
        generator.generateKeyPair();
    }

    @NonNull
    private static byte[] wrapKey(@NonNull PublicKey publicKey, @NonNull byte[] aesKey)
        throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(RSA_WRAP_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(aesKey);
    }

    @NonNull
    private static byte[] unwrapKey(@NonNull PrivateKey privateKey, @NonNull byte[] wrappedKey)
        throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(RSA_WRAP_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(wrappedKey);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private static final class Api23KeystoreHelper {

        @NonNull
        static byte[] encrypt(
                @NonNull String keyAlias,
                @NonNull byte[] plaintext,
                @Nullable byte[] associatedData) throws GeneralSecurityException {
            SecretKey key = getOrCreateAesKey(keyAlias);
            Cipher cipher = Cipher.getInstance(AesGcmSshSecretCipher.TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            if (associatedData != null && associatedData.length > 0) {
                cipher.updateAAD(associatedData);
            }
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(plaintext);
            return AesGcmSshSecretCipher.packPayload(iv, ciphertext);
        }

        @NonNull
        static byte[] decrypt(
                @NonNull String keyAlias,
                @NonNull byte[] ciphertextPayload,
                @Nullable byte[] associatedData) throws GeneralSecurityException {
            AesGcmSshSecretCipher.ParsedPayload parsed =
                AesGcmSshSecretCipher.unpackPayload(ciphertextPayload);
            SecretKey key = getOrCreateAesKey(keyAlias);
            Cipher cipher = Cipher.getInstance(AesGcmSshSecretCipher.TRANSFORMATION);
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                new GCMParameterSpec(AesGcmSshSecretCipher.GCM_TAG_LENGTH_BITS, parsed.iv)
            );
            if (associatedData != null && associatedData.length > 0) {
                cipher.updateAAD(associatedData);
            }
            return cipher.doFinal(parsed.ciphertext);
        }

        @NonNull
        private static SecretKey getOrCreateAesKey(@NonNull String keyAlias)
            throws GeneralSecurityException {
            try {
                KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
                keyStore.load(null);
                if (!keyStore.containsAlias(keyAlias)) {
                    KeyGenerator keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        ANDROID_KEYSTORE
                    );
                    KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                            keyAlias,
                            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(true)
                        .build();
                    keyGenerator.init(spec);
                    keyGenerator.generateKey();
                }
                SecretKey key = (SecretKey) keyStore.getKey(keyAlias, null);
                if (key == null) {
                    throw new GeneralSecurityException("Failed to load AndroidKeyStore key: " + keyAlias);
                }
                return key;
            } catch (IOException e) {
                throw new GeneralSecurityException("Failed to open AndroidKeyStore", e);
            }
        }
    }
}
