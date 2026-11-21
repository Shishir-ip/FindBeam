package com.findbeam.app.core.ble

/** Best-effort classification of a scanned device for nicer UI. */
enum class DeviceCategory { AUDIO, WEARABLE, TRACKER, PHONE, COMPUTER, OTHER }

object DeviceCategories {

    private val audio = listOf(
        "buds", "earbud", "airpod", "headphone", "headset", "earphone", "speaker",
        "soundcore", "liberty", "jbl", "beats", "tozo", "jawbone", "sennheiser",
        "wh-", "wf-", "jabra", "elite", "case", "soundbar", "sonos", "b&o", "boat"
    )
    private val wearables = listOf(
        "watch", "band", "fit", "amazfit", "garmin", "polar", "zen", "bip", "gts",
        "gtr", "stratos", "verge", "mi watch", "samsung fit", "redmi watch"
    )
    private val trackers = listOf(
        "airtag", "tile", "chipolo", "smarttag", "galaxy tag", "fob", "key", "tracker",
        "nut", "pebblebee", "cube"
    )
    private val phones = listOf(
        "iphone", "ipad", "galaxy s", "galaxy a", "galaxy m", "pixel", "redmi", "note ",
        "phone", "handset", "oneplus", "realme", "vivo", "oppo", "honor", "huawei"
    )
    private val computers = listOf(
        "macbook", "laptop", "thinkpad", "ideapad", "keyboard", "mouse", "macmini",
        "imac", "desktop", "logitech", "dell"
    )

    // BluetoothClass major device classes
    private const val MAJOR_MISC = 0x000
    private const val MAJOR_COMPUTER = 0x100
    private const val MAJOR_PHONE = 0x200
    private const val MAJOR_AUDIO = 0x400
    private const val MAJOR_PERIPHERAL = 0x500
    private const val MAJOR_IMAGING = 0x600
    private const val MAJOR_WEARABLE = 0x700
    private const val MAJOR_HEALTH = 0x900

    /**
     * Order of evidence: explicit name keywords first, then the Bluetooth
     * class of the physical device (very reliable when present), then
     * advertisement hints (service UUIDs, manufacturer id).
     */
    fun categorize(
        name: String?,
        companyId: Int?,
        serviceUuids: List<String>,
        classMajor: Int? = null
    ): DeviceCategory {
        val n = (name ?: "").lowercase()
        if (n.isNotEmpty()) {
            if (trackers.any { it in n }) return DeviceCategory.TRACKER
            if (audio.any { it in n }) return DeviceCategory.AUDIO
            if (wearables.any { it in n }) return DeviceCategory.WEARABLE
            if (computers.any { it in n }) return DeviceCategory.COMPUTER
            if (phones.any { it in n }) return DeviceCategory.PHONE
        }
        when (classMajor) {
            MAJOR_AUDIO -> return DeviceCategory.AUDIO
            MAJOR_WEARABLE -> return DeviceCategory.WEARABLE
            MAJOR_PERIPHERAL -> return DeviceCategory.WEARABLE
            MAJOR_HEALTH -> return DeviceCategory.WEARABLE
            MAJOR_COMPUTER -> return DeviceCategory.COMPUTER
            MAJOR_PHONE -> return DeviceCategory.PHONE
            MAJOR_IMAGING -> return DeviceCategory.OTHER
            MAJOR_MISC -> Unit
        }
        // Apple Find My network items (AirTag etc.) advertise this UUID.
        if (serviceUuids.any { it.startsWith("0000fd5d", true) }) return DeviceCategory.TRACKER
        if (serviceUuids.any { it.startsWith("0000180d", true) }) return DeviceCategory.WEARABLE
        return when (companyId) {
            0x004C -> DeviceCategory.OTHER      // Apple: too ambiguous to classify
            0x0006 -> DeviceCategory.COMPUTER   // Microsoft
            0x0171 -> DeviceCategory.AUDIO      // Anker / Soundcore
            else -> DeviceCategory.OTHER
        }
    }
}
