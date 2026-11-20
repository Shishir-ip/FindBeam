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
        "gtr", "stratos", "verge", "-redmi-watch", "mi watch", "samsung fit"
    )
    private val trackers = listOf(
        "airtag", "tile", "chipolo", "smarttag", "galaxy tag", "fob", "key", "tracker",
        "nut", "pebblebee", "cube tracker"
    )
    private val phones = listOf(
        "iphone", "ipad", "galaxy s", "galaxy a", "galaxy m", "pixel", "redmi", "note ",
        "phone", "handset", "oneplus", "realme", "vivo", "oppo", "honor", "huawei"
    )
    private val computers = listOf(
        "macbook", "laptop", "thinkpad", "ideapad", "keyboard", "mouse", "macmini",
        "imac", "desktop", "pc-", "logitech", "dell", "hp-"
    )

    fun categorize(name: String?, companyId: Int?, serviceUuids: List<String>): DeviceCategory {
        val n = (name ?: "").lowercase()
        if (n.isNotEmpty()) {
            if (trackers.any { it in n }) return DeviceCategory.TRACKER
            if (audio.any { it in n }) return DeviceCategory.AUDIO
            if (wearables.any { it in n }) return DeviceCategory.WEARABLE
            if (computers.any { it in n }) return DeviceCategory.COMPUTER
            if (phones.any { it in n }) return DeviceCategory.PHONE
        }
        // Apple Find My network items (AirTag etc.) advertise this UUID.
        if (serviceUuids.any { it.startsWith("0000fd5d", true) }) return DeviceCategory.TRACKER
        if (serviceUuids.any { it.startsWith("0000180d", true) }) return DeviceCategory.WEARABLE
        if (serviceUuids.any { it.startsWith("0000180f", true) || it.startsWith("0000fd6f", true) }) {
            return DeviceCategory.OTHER
        }
        return when (companyId) {
            0x004C -> DeviceCategory.OTHER      // Apple: too ambiguous to classify
            0x0006 -> DeviceCategory.COMPUTER   // Microsoft
            0x0075 -> DeviceCategory.OTHER      // Samsung
            0x0171 -> DeviceCategory.AUDIO      // Anker / Soundcore
            else -> DeviceCategory.OTHER
        }
    }
}
