package org.schabi.newpipe

import android.content.Context
import androidx.preference.PreferenceManager
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import okhttp3.Dns

/**
 * The IP version the user wants the app to use for network connections.
 *
 * The persisted value (see [fromPreferences]) is one of the `ip_version_*_value` string
 * resources; this enum is the in-memory representation used by [IpVersionDns] and by the
 * JVM-system-property logic in [App.attachBaseContext].
 */
enum class IpVersionMode {
    SYSTEM,
    IPV4,
    IPV6
    ;

    companion object {
        /** Map a persisted `ip_version_*_value` string (an entryValue) to a mode. */
        @JvmStatic
        fun fromPreferenceValue(context: Context, value: String?): IpVersionMode = when (value) {
            context.getString(R.string.ip_version_ipv4_value) -> IPV4
            context.getString(R.string.ip_version_ipv6_value) -> IPV6
            else -> SYSTEM
        }

        /** Read the currently saved IP version mode from the default SharedPreferences. */
        @JvmStatic
        fun fromPreferences(context: Context): IpVersionMode {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val value = prefs.getString(
                context.getString(R.string.ip_version_key),
                context.getString(R.string.ip_version_default_value)
            )
            return fromPreferenceValue(context, value)
        }
    }
}

/**
 * A custom OkHttp [Dns] that lets the user pin connections to IPv4 or IPv6.
 *
 * OkHttp calls [lookup] right before opening a socket to turn a hostname into a list of IP
 * addresses; by returning only IPv4 (or only IPv6) addresses we make OkHttp connect over that
 * address family. We delegate the actual name resolution to [Dns.SYSTEM] and only filter the
 * result.
 *
 * This is a singleton so its [mode] can be updated at runtime (e.g. from the settings screen)
 * without rebuilding the shared [okhttp3.OkHttpClient]: [lookup] re-reads [mode] on every call.
 */
object IpVersionDns : Dns {
    @Volatile
    var mode: IpVersionMode = IpVersionMode.SYSTEM

    override fun lookup(hostname: String): List<InetAddress> {
        val all = Dns.SYSTEM.lookup(hostname)
        val filtered = when (mode) {
            IpVersionMode.SYSTEM -> all
            IpVersionMode.IPV4 -> all.filterIsInstance<Inet4Address>()
            IpVersionMode.IPV6 -> all.filterIsInstance<Inet6Address>()
        }

        // If filtering left nothing (e.g. "Prefer IPv6" on an IPv4-only network), fall back to the
        // full list so we raise a normal connection error instead of an UnknownHostException.
        return filtered.ifEmpty { all }
    }
}
