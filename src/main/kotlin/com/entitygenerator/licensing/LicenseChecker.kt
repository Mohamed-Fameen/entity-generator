package com.entitygenerator.licensing

import com.intellij.ui.LicensingFacade

object LicenseChecker {

    // Leave blank until JetBrains assigns a real product code after your freemium application
    // is approved. While blank, Pro features are treated as free for everyone -- this is the
    // correct state for your initial free release.
    private const val PRODUCT_CODE = ""

    fun isProLicensed(): Boolean {
        if (PRODUCT_CODE.isBlank()) return true // not yet converted to freemium -- everything is free

        // getInstance() can be null very early during IDE startup (license data loads
        // asynchronously) -- treat that as "not licensed yet" rather than crashing.
        val facade = LicensingFacade.getInstance() ?: return false
        return facade.getConfirmationStamp(PRODUCT_CODE) != null
    }
}