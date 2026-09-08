package com.entitygenerator.db

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

object ConnectionCredentialsStore {

    private fun attributesFor(connectionId: String): CredentialAttributes =
        CredentialAttributes(generateServiceName("EntityGenerator", connectionId))

    fun save(connectionId: String, username: String, password: String) {
        PasswordSafe.instance.set(attributesFor(connectionId), Credentials(username, password))
    }

    fun loadPassword(connectionId: String): String? =
        PasswordSafe.instance.getPassword(attributesFor(connectionId))

    fun delete(connectionId: String) {
        PasswordSafe.instance.set(attributesFor(connectionId), null)
    }
}