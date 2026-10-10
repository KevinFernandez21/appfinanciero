package com.kevinfernandez.appfinanciero.backup

import android.content.pm.ApplicationInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.kevinfernandez.appfinanciero.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

/** The platform must not transfer the plaintext Room database outside explicit SAF export. */
class PlatformBackupRulesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val domains = setOf("root", "file", "database", "sharedpref", "external",
        "device_root", "device_file", "device_database", "device_sharedpref")

    @Test fun manifestOptsOutAndModernRulesExcludeEveryStorageDomain() {
        val app = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals(0, app.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        val sections = exclusions(R.xml.data_extraction_rules)
        assertEquals(domains, sections["cloud-backup"])
        assertEquals(domains, sections["device-transfer"])
    }

    @Test fun legacyFullBackupRulesExcludeEveryStorageDomain() {
        assertEquals(domains, exclusions(R.xml.full_backup_rules)["full-backup-content"])
    }

    private fun exclusions(resourceId: Int): Map<String, Set<String>> {
        val found = mutableMapOf<String, MutableSet<String>>()
        val xml = context.resources.getXml(resourceId)
        var section: String? = null
        try {
            while (xml.eventType != XmlPullParser.END_DOCUMENT) {
                when (xml.eventType) {
                    XmlPullParser.START_TAG -> when (xml.name) {
                        "cloud-backup", "device-transfer", "full-backup-content" -> section = xml.name
                        "exclude" -> if (xml.getAttributeValue(null, "path") in setOf(".", "./")) {
                            section?.let { found.getOrPut(it) { mutableSetOf() }.add(xml.getAttributeValue(null, "domain")) }
                        }
                    }
                    XmlPullParser.END_TAG -> if (xml.name == section) section = null
                }
                xml.next()
            }
        } finally { xml.close() }
        return found
    }
}
