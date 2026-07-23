package com.zy.email.utils

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 加密工具类 - 用于加密存储邮箱密码
 * 使用AES/GCM/NoPadding算法
 */
object EncryptionUtils {
    
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12
    private const val KEY_SIZE = 256
    
    // 简单的密钥派生 - 生产环境应使用Android Keystore
    private val masterKey: SecretKey by lazy {
        val keyString = "ZYEmail2024MasterKeySecureString!"
        val keyBytes = keyString.substring(0, 32).toByteArray(Charsets.UTF_8)
        SecretKeySpec(keyBytes, ALGORITHM)
    }
    
    /**
     * 加密文本
     */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return plainText
        
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, masterKey)
            
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            
            // 组合 IV + 加密数据
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
            
            Base64.encodeToString(combined, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            plainText
        }
    }
    
    /**
     * 解密文本
     */
    fun decrypt(encryptedText: String): String {
        if (encryptedText.isEmpty()) return encryptedText
        
        return try {
            val combined = Base64.decode(encryptedText, Base64.DEFAULT)
            
            // 分离 IV 和加密数据
            val iv = combined.copyOfRange(0, IV_LENGTH_BYTE)
            val encryptedBytes = combined.copyOfRange(IV_LENGTH_BYTE, combined.size)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
            cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)
            
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            encryptedText
        }
    }
    
    /**
     * 检查字符串是否已加密（简单的启发式检查）
     */
    fun isEncrypted(text: String): Boolean {
        if (text.isEmpty() || text.length < 16) return false
        return try {
            Base64.decode(text, Base64.DEFAULT) != null
        } catch (e: Exception) {
            false
        }
    }
}
