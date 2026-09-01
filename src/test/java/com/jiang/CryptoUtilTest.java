package com.jiang;

import com.jiang.util.CryptoUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CryptoUtil 加密工具单测 — 项目首个纯断言单元测试（不联网）。
 * <p>
 * 覆盖 BYOK 落库加密的关键不变量：
 * 1. 加密-解密闭环一致；
 * 2. 密钥错误/密文被篡改必须失败（GCM 完整性校验），而非返回乱码；
 * 3. 空值不落密文；
 * 4. 脱敏只回显后四位。
 */
class CryptoUtilTest {

    @Test
    void roundTripPreservesPlaintext() {
        String secret = "unit-test-secret";
        String plain = "sk-" + "abcdefghijklmnopqrstuvwxyz012345";
        String enc = CryptoUtil.encrypt(plain, secret);
        assertNotNull(enc);
        assertNotEquals(plain, enc, "密文不应等于明文");
        assertEquals(plain, CryptoUtil.decrypt(enc, secret), "解密应还原明文");
    }

    @Test
    void wrongSecretMustFail() {
        String enc = CryptoUtil.encrypt("sk-abc", "secret-a");
        assertThrows(IllegalStateException.class, () -> CryptoUtil.decrypt(enc, "secret-b"),
                "密钥错误时 GCM 应抛异常而非返回乱码");
    }

    @Test
    void tamperedCipherMustFail() {
        String enc = CryptoUtil.encrypt("sk-abc", "secret");
        // 翻转密文中间一个字符
        char[] chars = enc.toCharArray();
        int mid = chars.length / 2;
        chars[mid] = chars[mid] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);
        assertThrows(IllegalStateException.class, () -> CryptoUtil.decrypt(tampered, "secret"),
                "密文被篡改应被 GCM 认证标签拦截");
    }

    @Test
    void emptyInputReturnsNull() {
        assertNull(CryptoUtil.encrypt("", "s"));
        assertNull(CryptoUtil.decrypt("", "s"));
        assertNull(CryptoUtil.decrypt(null, "s"));
    }

    @Test
    void maskOnlyShowsLastFour() {
        assertEquals("****1234", CryptoUtil.mask("sk-abcdef1234"));
        assertEquals("****ab", CryptoUtil.mask("ab"));
        assertEquals("", CryptoUtil.mask(""));
        assertEquals("", CryptoUtil.mask(null));
    }
}