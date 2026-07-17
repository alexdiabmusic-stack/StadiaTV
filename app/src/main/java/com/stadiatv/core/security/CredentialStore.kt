package com.stadiatv.core.security

interface CredentialStore {
    suspend fun putSecret(sourceId: String, name: String, secret: String)
    suspend fun getSecret(sourceId: String, name: String): String?
    suspend fun deleteSecret(sourceId: String, name: String)
    suspend fun deleteAll(sourceId: String)
}
