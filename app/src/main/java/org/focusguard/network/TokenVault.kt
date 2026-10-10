package org.focusguard.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class TokenVault(context: Context) {
    private val storage=context.getSharedPreferences("auth-vault",Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("focusguard-auth",null) as? SecretKey)?.let { return it }
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder("focusguard-auth",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }
    @Synchronized fun put(name: String,value: String) {
        if(value.isEmpty()) { storage.edit().remove(name).commit(); return }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key())
        val bytes=cipher.iv+cipher.doFinal(value.toByteArray())
        check(storage.edit().putString(name,Base64.encodeToString(bytes,Base64.NO_WRAP)).commit())
    }
    @Synchronized fun get(name: String): String {
        val raw=storage.getString(name,null) ?: return ""
        return try { val bytes=Base64.decode(raw,Base64.NO_WRAP); val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12))); String(cipher.doFinal(bytes.copyOfRange(12,bytes.size))) } catch(e: Exception) { "" }
    }
    fun clear() { storage.edit().clear().commit() }
}
