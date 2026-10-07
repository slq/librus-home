package pl.librushome.android;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Only ciphertext is written. The AES key stays in Android Keystore. */
public final class SecureStore {
    private final String alias;
    private static final int MAX_BYTES = 34 * 1024 * 1024;
    private final AtomicFile file;

    public SecureStore(Context context) {
        this(context, "state.aes", "LibrusApp.local.v1");
    }

    SecureStore(Context context, String filename, String keyAlias) {
        file = new AtomicFile(new File(context.getNoBackupFilesDir(), filename));
        alias = keyAlias;
    }

    private SecretKey key(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(alias)) return (SecretKey) store.getKey(alias, null);
        if (!create) throw new IllegalStateException("No decryption key");
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }

    public String read() throws Exception {
        if (!file.getBaseFile().exists()) return "";
        if (file.getBaseFile().length() > MAX_BYTES) throw new IllegalStateException("Oversize state");
        byte[] data = file.readFully();
        if (data.length < 30 || data[0] != 1 || data[1] != 12) throw new IllegalStateException("Invalid state");
        byte[] iv = java.util.Arrays.copyOfRange(data, 2, 14);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(data, 14, data.length - 14), StandardCharsets.UTF_8);
    }

    public void write(String json) throws Exception {
        byte[] plain = json.getBytes(StandardCharsets.UTF_8);
        if (plain.length > MAX_BYTES - 64) throw new IllegalStateException("Oversize state");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(true));
        byte[] iv = cipher.getIV();
        if (iv.length != 12) throw new IllegalStateException("Invalid IV");
        byte[] encrypted = cipher.doFinal(plain);
        byte[] output = ByteBuffer.allocate(2 + iv.length + encrypted.length)
                .put((byte) 1).put((byte) iv.length).put(iv).put(encrypted).array();
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            stream.write(output);
            file.finishWrite(stream);
        } catch (Exception error) {
            if (stream != null) file.failWrite(stream);
            throw error;
        }
    }

    public void clear() throws Exception {
        file.delete();
        if (file.getBaseFile().exists()) throw new IllegalStateException("State still exists");
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
        keys.deleteEntry(alias);
    }
}
