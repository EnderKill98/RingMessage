package me.enderkill98.ringmessage.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

public class NoChatReportsUtil {

    public record DetailedDecryptionInfo(String decryptedText, int keyIndex, @Nullable String encapsulation,
                                         @Nullable Compression compression, @Nullable Float compressionRatio) { }
    public enum CompressionPolicy {
        WhenNecessary,
        Preferred,
        Always,
        Never;

        public static CompressionPolicy fromNcrInstance(Object instanceObject) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
            String name = (String) Enum.class.getDeclaredMethod("name").invoke(instanceObject);
            for(CompressionPolicy policy : values()) {
                if(policy.name().equals(name)) return policy;
            }
            return null;
        }

        public Object toNcrInstance() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
            String name = this.name();
            for(Object maybeInstance : getCompressionPolicyClass().getEnumConstants()) {
                if(((String) Enum.class.getDeclaredMethod("name").invoke(maybeInstance)).equals(name))
                    return maybeInstance;
            }
            return null;
        }
    }

    public record Compression(@NotNull Object instance) {
        public String getCompressionName() {
            try {
                return (String) getCompressionClass().getDeclaredMethod("getCompressionName").invoke(this.instance);
            }catch (Exception ex) {
                ex.printStackTrace();
                return null;
            }
        }

        public Compression getRegisteredBrotliCompression() {
            try {
                return new Compression(getCompressionClass().getDeclaredField("COMPRESSION_BROTLI").get(null));
            }catch (Exception ex) {
                ex.printStackTrace();
                return null;
            }
        }

        public Compression getRegisteredCustomCompression() {
            try {
                return new Compression(getCompressionClass().getDeclaredField("COMPRESSION_CUSTOM").get(null));
            }catch (Exception ex) {
                ex.printStackTrace();
                return null;
            }
        }
    }

    public static boolean isModAvailable() {
        try {
            Class.forName("com.aizistral.nochatreports.common.config.NCRConfig");
            return true;
        }catch (ClassNotFoundException ex) {
            return false;
        }
    }

    public static Class<?> getEncryptorClass() {
        try {
            return Class.forName("com.aizistral.nochatreports.common.encryption.Encryptor");
        } catch (ClassNotFoundException ex) {}
        return null;
    }

    public static Object getEncryptorInstance() {
        try {
            Class classNCRConfig = Class.forName("com.aizistral.nochatreports.common.config.NCRConfig");
            Class classNCRConfigEncryption = Class.forName("com.aizistral.nochatreports.common.config.NCRConfigEncryption");
            Class<?> classEncryptor = Class.forName("com.aizistral.nochatreports.common.encryption.Encryptor");
            Object ncrConfigEncryptionInstance = classNCRConfig.getMethod("getEncryption").invoke(null);
            Optional<Object> maybeEncryptorInstance = (Optional<Object>) classNCRConfigEncryption.getMethod("getEncryptor").invoke(ncrConfigEncryptionInstance);
            return maybeEncryptorInstance.orElse(null);
        }catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public static Object newAESCFB8EncryptorInstance(int keyIndex, String encapsulation) {
        try {
            Class classNCRConfig = Class.forName("com.aizistral.nochatreports.common.config.NCRConfig");
            Class classNCRConfigEncryption = Class.forName("com.aizistral.nochatreports.common.config.NCRConfigEncryption");
            Object ncrConfigEncryptionInstance = classNCRConfig.getMethod("getEncryption").invoke(null);
            String encryptionKeys = (String) classNCRConfigEncryption.getDeclaredMethod("getEncryptionKey").invoke(ncrConfigEncryptionInstance);
            String key = encryptionKeys.split(",")[keyIndex];

            Class<?> classEncryption = Class.forName("com.aizistral.nochatreports.common.encryption.Encryption");
            Class<?> classAesCfb8Encryption = Class.forName("com.aizistral.nochatreports.common.encryption.AESCFB8Encryption");
            //Class<?> classAesCfb8Encryptor = Class.forName("com.aizistral.nochatreports.common.encryption.AESCFB8Encryptor");

            Object aesCfb8EncryptionInstance = classEncryption.getDeclaredField("AES_CFB8_" + encapsulation.toUpperCase()).get(null);
            Object aesCfb8EncryptorInstance = classAesCfb8Encryption.getDeclaredMethod("getProcessor", String.class).invoke(aesCfb8EncryptionInstance, key);
            return aesCfb8EncryptorInstance;
        }catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public static Class<?> getCompressionPolicyClass() {
        try {
            return Class.forName("com.aizistral.nochatreports.common.config.NCRConfigEncryption$CompressionPolicy");
        } catch (ClassNotFoundException ex) {}
        return null;
    }

    public static Class<?> getCompressionClass() {
        try {
            return Class.forName("com.aizistral.nochatreports.common.compression.Compression");
        } catch (ClassNotFoundException ex) {}
        return null;
    }
    public static String encrypt(String plain) {
        try {
            return (String) getEncryptorClass().getMethod("encrypt", String.class).invoke(getEncryptorInstance(), "#%" + plain);
        }catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public static String encrypt(int keyIndex, String encapsulation, String plain) {
        try {
            Class<?> classAesCfb8Encryptor = Class.forName("com.aizistral.nochatreports.common.encryption.AESCFB8Encryptor");
            Object aesCfb8EncryptorInstance = newAESCFB8EncryptorInstance(keyIndex, encapsulation);
            return (String) classAesCfb8Encryptor.getMethod("encrypt", String.class).invoke(aesCfb8EncryptorInstance, "#%" + plain);
        }catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public static String encryptAndCompress(int keyIndex, String encapsulation, @Nullable String plaintextPrefix, String plain, @NotNull CompressionPolicy policy, @Nullable Compression specificCompression) {
        if(plaintextPrefix == null) plaintextPrefix = "";
        try {
            Class<?> classAesCfb8Encryptor = Class.forName("com.aizistral.nochatreports.common.encryption.AESCFB8Encryptor");
            Object aesCfb8EncryptorInstance = newAESCFB8EncryptorInstance(keyIndex, encapsulation);
            return (String) classAesCfb8Encryptor.getMethod("encryptAndCompress", String.class, String.class, getCompressionPolicyClass(), getCompressionClass())
                    .invoke(aesCfb8EncryptorInstance, plaintextPrefix, "#%" + plain, policy.toNcrInstance(), specificCompression == null ? null : specificCompression.instance);
        }catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public static String decrypt(String encrypted) {
        try {
            String decrypted = (String) getEncryptorClass().getMethod("decrypt", String.class).invoke(getEncryptorInstance(), encrypted);
            if(!decrypted.startsWith("#%")) return null;
            return decrypted.substring(2);
        }catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public static boolean supportsDecryptDetailed() {
        try {
            Class encryptionUtilClass = Class.forName("com.aizistral.nochatreports.common.core.EncryptionUtil");
            encryptionUtilClass.getDeclaredMethod("tryDecryptDetailed", String.class);
            return true;
        }catch (ClassNotFoundException | NoSuchMethodException ex) {
            return false;
        }
    }

    public static Optional<DetailedDecryptionInfo> tryDecryptDetailed(String maybeEncrypted) {
        try {
            Class encryptionUtilClass = Class.forName("com.aizistral.nochatreports.common.core.EncryptionUtil");
            Optional<Object> maybeInfo = (Optional<Object>) encryptionUtilClass.getDeclaredMethod("tryDecryptDetailed", String.class).invoke(null, maybeEncrypted);
            if(maybeInfo.isEmpty()) return Optional.empty();
            Object info = maybeInfo.get();
            Class infoClass = info.getClass();
            Object compressionInstance =  infoClass.getDeclaredMethod("compression").invoke(info);
            return Optional.of(
                    new DetailedDecryptionInfo(
                        (String) infoClass.getDeclaredMethod("getDecryptedText").invoke(info),
                        (int) infoClass.getDeclaredMethod("keyIndex").invoke(info),
                        (String) infoClass.getDeclaredMethod("encapsulation").invoke(info),
                        compressionInstance == null ? null : new Compression(compressionInstance),
                        (Float) infoClass.getDeclaredMethod("compressionRatio").invoke(info)
                    )

            );
        }catch (Exception ex) {
            ex.printStackTrace();
            return Optional.empty();
        }
    }

    public static Optional<String> tryDecrypt(String maybeEncrypted) {
        try {
            Class encryptionUtilClass = Class.forName("com.aizistral.nochatreports.common.core.EncryptionUtil");
            return (Optional<String>) encryptionUtilClass.getDeclaredMethod("tryDecrypt", String.class, getEncryptorClass()).invoke(null, maybeEncrypted, getEncryptorInstance());
        }catch (Exception ex) {
            ex.printStackTrace();
            return Optional.empty();
        }
    }

    public static boolean shouldEncryptPublic() {
        try {
            Class classNCRConfig = Class.forName("com.aizistral.nochatreports.common.config.NCRConfig");
            Class classNCRConfigEncryption = Class.forName("com.aizistral.nochatreports.common.config.NCRConfigEncryption");
            Object ncrConfigEncryptionInstance = classNCRConfig.getMethod("getEncryption").invoke(null);
            return (boolean) classNCRConfigEncryption.getMethod("shouldEncryptPublic").invoke(ncrConfigEncryptionInstance);
        }catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }
    public static boolean isEnabled() {
        try {
            Class classNCRConfig = Class.forName("com.aizistral.nochatreports.common.config.NCRConfig");
            Class classNCRConfigEncryption = Class.forName("com.aizistral.nochatreports.common.config.NCRConfigEncryption");
            Object ncrConfigEncryptionInstance = classNCRConfig.getMethod("getEncryption").invoke(null);
            return (boolean) classNCRConfigEncryption.getMethod("isEnabled").invoke(ncrConfigEncryptionInstance);
        }catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

}
