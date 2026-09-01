package com.jiang.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 加解密工具 — 用于用户自填 API Key 的落库加密。
 * <p>
 * 每次加密使用随机 12 字节 IV，输出 = base64(iv ‖ ciphertext)；解密时取前 12 字节为 IV。
 * GCM 自带完整性校验：密钥错误或密文被篡改会抛异常，而非得到乱码。
 * 密钥由配置 {@code app.llm-key-secret} 经 SHA-256 派生为 32 字节，简单口令也能用。
 */
public final class CryptoUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int IV_LEN = 12;    // GCM 推荐 IV 长度
    private static final int TAG_BITS = 128; // GCM 认证标签位数

    private CryptoUtil() {
    }

    /**
     * 加密为 base64(iv ‖ ciphertext)。明文为空返回 null。
     */
    public static String encrypt(String plaintext, String secret) {
        if (plaintext == null || plaintext.isEmpty()) return null;
        byte[] key = deriveKey(secret);
        byte[] iv = new byte[IV_LEN];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_LEN + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ct, 0, out, IV_LEN, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("API Key 加密失败", e);
        }
    }

    /**
     * 解密 encrypt() 的输出。密文为空返回 null；密钥错误/被篡改抛异常。
     */
    public static String decrypt(String encrypted, String secret) {
        if (encrypted == null || encrypted.isEmpty()) return null;
        byte[] in = Base64.getDecoder().decode(encrypted);
        if (in.length < IV_LEN + 1) throw new IllegalArgumentException("密文格式错误");
        byte[] key = deriveKey(secret);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, in, 0, IV_LEN));
            return new String(cipher.doFinal(in, IV_LEN, in.length - IV_LEN), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("API Key 解密失败", e);
        }
    }

    /** 配置口令不可直接当 AES key 用（长度不达标），SHA-256 派生到固定 32 字节。 */
    private static byte[] deriveKey(String secret) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("密钥派生失败", e);
        }
    }

    /** 明文 key 脱敏：只回显后四位，用于前端"已配置 ****1234"。 */
    public static String mask(String plainKey) {
        if (plainKey == null || plainKey.isEmpty()) return "";
        int len = plainKey.length();
        String tail = len >= 4 ? plainKey.substring(len - 4) : plainKey;
        return "****" + tail;
    }
}